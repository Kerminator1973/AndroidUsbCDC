package ru.dors.androidusbcdc

import android.content.Context
import android.util.Log
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicReference

/**
 * Central service managing the state and logic for USB CDC communication.
 * This class abstracts complex IO and concurrency away from the Activity.
 */
class UsbConnectionManager(
    private val context: Context,
    private val usbManager: UsbManager
) {
    // State Flow for observing the list of available ports (Reactive Programming)
    private val _availablePorts = MutableStateFlow<List<CdcPortData>>(emptyList())
    val availablePorts: StateFlow<List<CdcPortData>> = _availablePorts.asStateFlow()

    // State for the selected port index
    private val _selectedPortIndex = MutableStateFlow<Int?>(null)
    val selectedPortIndex: StateFlow<Int?> = _selectedPortIndex.asStateFlow()

    // --- Internal State ---
    private var connectedUsbPort: UsbSerialPort? = null
    private var serialInputOutputManager: SerialInputOutputManager? = null
    private var currentConnection: android.hardware.usb.UsbDeviceConnection? = null

    // Use AtomicReference to safely manage mutable connection state across threads/coroutines
    private val isConnected = AtomicReference(false)

    // Protocol configuration properties
    var useDSlipProtocol: Boolean = true
    var isUseDefaultSpeed: Boolean = true

    // --- Public API Methods ---

    /**
     * Sets the operational parameters (DSlip/CCNet, Default Speed).
     */
    fun setProtocolParams(useDSlip: Boolean, useDefaultSpeed: Boolean) {
        useDSlipProtocol = useDSlip
        isUseDefaultSpeed = useDefaultSpeed
    }

    fun getProtocolRequest(): ByteArray {
        return if (useDSlipProtocol)
            byteArrayOf(0xB4.toByte(), 0x00, 0x81.toByte(), 0x00, 0x74)
        else
            byteArrayOf(0x02, 0x03, 0x06, 0x37, 0xFE.toByte(), 0xC7.toByte())
    }

    /**
     * Scans and retrieves the list of available ports attached to the first discovered driver.
     * This method is safe to call on background threads.
     */
    fun getAvailablePorts(): List<CdcPortData> {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) return emptyList()

        val driver = availableDrivers[0]
        var connection: android.hardware.usb.UsbDeviceConnection? = null

        try {
            connection = usbManager.openDevice(driver.device)
            if (connection == null) return emptyList()

            val ports = mutableListOf<CdcPortData>()
            for (port in driver.ports) {
                try {
                    port.open(connection)
                    val writeEndpointAddr = if (port.writeEndpoint != null) port.writeEndpoint.address else 0
                    val readEndpointAddr = if (port.readEndpoint != null) port.readEndpoint.address else 0
                    ports.add(CdcPortData(port.portNumber, writeEndpointAddr, readEndpointAddr))
                } catch (e: Exception) {
                    Log.e("USB", "Failed to process port ${port.portNumber}", e)
                    ports.add(CdcPortData(0, 0, 0))
                }
            }
            return ports
        } finally {
            // Ensure connection is closed immediately after listing ports
            connection?.close()
        }
    }

    /**
     * Updates the internal state of available ports. Must be called after discovery.
     */
    fun updateAvailablePorts(ports: List<CdcPortData>) {
        _availablePorts.value = ports
        // Note: In a real MVVM setup, this would trigger a ViewModel update,
        // which would then update the adapter in the Activity.
    }

    /**
     * Records the user's selection for the port index.
     */
    fun setSelectedPort(index: Int) {
        _selectedPortIndex.value = index
    }

    /**
     * Executes the full connection lifecycle: disconnects existing stream,
     * establishes new connection, configures parameters, and starts streaming.
     *
     * @return True if connection was successful, false otherwise.
     */
    suspend fun connect(driver: UsbSerialDriver): Boolean = withContext(Dispatchers.IO) {
        // 1. Cleanup existing state
        disconnect()

        val selectedPortIndex = _selectedPortIndex.value ?: run {
            Log.e("USB", "No port selected.")
            return@withContext false
        }

        val portToUse = driver.ports.getOrNull(selectedPortIndex)
        if (portToUse == null) {
            Log.e("USB", "Selected port object is invalid.")
            return@withContext false
        }

        try {
            // 2. Open connection (requires the UsbManager object, omitted here for brevity but assumed to be available)
            val connection = usbManager.openDevice(driver.device) ?: throw IllegalStateException("Failed to get UsbConnection.")

            // 3. Open port and configure
            portToUse.open(connection)
            val speed = if (isUseDefaultSpeed) 115200 else 921600
            portToUse.setParameters(speed, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // 4. Activate signals and save state
            portToUse.dtr = true
            portToUse.rts = true

            connectedUsbPort = portToUse
            currentConnection = connection
            isConnected.set(true)

            return@withContext true
        } catch (e: Exception) {
            Log.e("USB", "Connection failure", e)
            disconnect()
            return@withContext false
        }
    }

    /**
     * Starts the background data stream listener using Kotlin Coroutines.
     * This method must only be called when the connection is active.
     */
    suspend fun startDataStream(port: UsbSerialPort) {
        if (!isConnected.get()) return

        // Use a dedicated listener implementation
        val listener = object : SerialInputOutputManager.Listener {
            override fun onRunError(ignored: Exception) {
                Log.e("USB_STREAM", "Run Error occurred.")
                // Potentially trigger UI update to show error
            }
            override fun onNewData(data: ByteArray) {
                // This callback is executed on the background thread managed by the library.
                // We must launch a coroutine on the Main Dispatcher to update the UI.
                // In a ViewModel context, this data would be emitted via a Flow.

                val activity = context as? MainActivity ?: return

                // TODO: посылать данные в главное окно
                //activity.handleIncomingData(data.toHex())
            }
        }

        // Initialize and start the I/O manager
        val manager = SerialInputOutputManager(port, listener)
        manager.readTimeout = 0
        manager.start()
        serialInputOutputManager = manager
    }

    /**
     * Gracefully closes the USB connection and resets the internal state.
     */
    fun disconnect() {
        try {
            connectedUsbPort?.close()
            serialInputOutputManager?.stop()
        } catch (e: Exception) {
            Log.w("USB_MAN", "Error during disconnect cleanup.", e)
        } finally {
            connectedUsbPort = null
            serialInputOutputManager = null
            currentConnection = null
            isConnected.set(false)
        }
    }

    /**
     * Wrapper function for the complex connection initiation flow.
     * Used by MainActivity to manage the driver/port selection steps.
     */
    suspend fun initiateConnectionExchange(): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            return false
        }

        val driver = availableDrivers[0]

        val success = connect(driver)

        if (success) {
            // Start the data streaming job only if connection was successful
            startDataStream(connectedUsbPort!!)
        }
        return success
    }
}
