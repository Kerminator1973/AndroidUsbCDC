package ru.dors.androidusbcdc

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager

/**
 * Manages the connection and data transfer over USB CDC serial port.
 * This class is responsible only for low-level hardware communication.
 */
class UsbConnectionManager(private val context: Context) {

    // --- Internal State ---
    private var mPort: UsbSerialPort? = null
    var serialInputOutputManager: SerialInputOutputManager? = null

    // Callback interface to report data/errors back to the UI layer (MainActivity)
    interface ConnectionListener {
        fun onDataReceived(hexString: String)
        fun onError(message: String)
        fun onConnectionSuccess(message: String)
        fun onConnectionFailure(message: String)
    }

    private var listener: ConnectionListener? = null

    // --- Properties and Initialization ---
    var currentSelectedPortIndex: Int = 0

    /**
     * Sets the callback listener. All communication events will be reported through this object.
     */
    fun setConnectionListener(listener: ConnectionListener) {
        this.listener = listener
    }


    // ******************** CONNECTION MANAGEMENT ********************

    /**
     * Attempts to enumerate and populate the list of available ports based on a given UsbManager.
     * @param connection ...
     * @param driver The detected USB serial device driver.
     * @return A list of CdcPortData objects.
     */
    fun getAvailablePorts(connection: UsbDeviceConnection, driver: UsbSerialDriver): List<CdcPortData> {
        val portList = mutableListOf<CdcPortData>()
        for (port in driver.ports) {
            try {
                port.open(connection)

                var writeEndpointAddr = 0
                if (port.writeEndpoint != null) {
                    writeEndpointAddr = port.writeEndpoint.address
                }

                var readEndpointAddr = 0
                if (port.readEndpoint != null) {
                    readEndpointAddr = port.readEndpoint.address
                }
                portList.add(CdcPortData(port.portNumber, writeEndpointAddr, readEndpointAddr))
            } catch (e: Exception) {
                // Handle ports that fail to open
                portList.add(CdcPortData(0, 0, 0))
            }
        }
        return portList
    }

    /**
     * Establishes the connection using the device and selected port index.
     * @param manager The Android UsbManager.
     * @param driver The detected USB serial device driver.
     */
    fun connectToPort(manager: android.hardware.usb.UsbManager, driver: UsbSerialDriver, selectedPort: Int?) {
        // 1. Clean up previous connection
        disconnect()

        if (selectedPort == null) {
            listener?.onError("Error: No port selected.")
            return
        }

        try {
            val connection = manager.openDevice(driver.device) ?: run {
                listener?.onConnectionFailure("Could not open device connection.")
                return
            }

            // 2. Set the active port
            mPort = driver.ports[selectedPort]
            if (mPort == null) {
                listener?.onConnectionFailure("Failed to select port index $currentSelectedPortIndex.")
                return
            }

            mPort?.open(connection)

            // 3. Set parameters and signals
            val prefs = AppPreferences(context) // Assuming this object is available for settings reading
            val speed = if (prefs.useDefaultSpeed) 115200 else 921600
            mPort?.setParameters(speed, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Signal readiness
            mPort?.dtr = true
            mPort?.rts = true

            listener?.onConnectionSuccess("Successfully connected to port ${selectedPort}.")


        } catch (e: Exception) {
            listener?.onError("Failed to establish connection: " + e.message)
        }
    }

    // ******************** DATA HANDLING AND LIFECYCLE ********************

    /**
     * Initializes and starts listening for incoming serial data. Must be called after connectToPort().
     */
    fun startListening() {
        if (mPort == null) return

        val serialInputOutputListener = object : SerialInputOutputManager.Listener {
            override fun onRunError(errorMsg: Exception) {
                // Report errors to the UI
                listener?.onError("Runtime Error: ${errorMsg.message}")
            }

            override fun onNewData(data: ByteArray) {
                // Pass data bytes back for hex conversion/display in the UI
                val hexString = data.toHex() + "\n"
                listener?.onDataReceived(hexString)
            }
        }

        serialInputOutputManager = SerialInputOutputManager(mPort!!, serialInputOutputListener)
        serialInputOutputManager!!.readTimeout = 0
        serialInputOutputManager!!.start()
    }


    /**
     * Sends a specific command byte array to the connected microcontroller.
     */
    fun sendCommand(command: ByteArray) {
        if (mPort == null) {
            listener?.onError("Cannot send command: No active port connection.")
            return
        }
        try {
            mPort?.write(command, 0)
        } catch (e: Exception) {
            listener?.onError("Error sending command: ${e.message}")
        }
    }

    /**
     * Closes the serial connection and resets internal state.
     */
    fun disconnect() {
        // Ensure this is called to clean up resources
        mPort?.close()
        serialInputOutputManager?.stop()
        mPort = null
        serialInputOutputManager = null
        listener?.onConnectionSuccess("Disconnected from USB CDC.")
    }

    /**
     * Getter for the current port object.
     */
    fun getPort(): UsbSerialPort? = mPort
}
