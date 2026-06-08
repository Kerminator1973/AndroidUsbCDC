package ru.dors.androidusbcdc

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.UsbSerialInputOutputManager
import android.hardware.usb.UsbManager

class MainActivity : AppCompatActivity() {

    // Use Kotlin Coroutine Scope for structured concurrency
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())

    // Dependencies/State
    private lateinit var usbConnectionManager: UsbConnectionManager
    private lateinit var connectionMessageView: TextView
    private lateinit var listView: ListView
    private lateinit var portButton: Button
    private lateinit var exchangeButton: Button

    // Live Data/Flow handling
    private var dataStreamJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()

        // 1. Initialize the USB Manager (Dependency Injection/Service Locator pattern simulation)
        usbConnectionManager = UsbConnectionManager(this, getSystemService(Context.USB_SERVICE) as UsbManager)
        
        // 2. Set up Listeners using modern Kotlin practices
        setupListeners()

        // 3. Initialize and bind the adapter
        val initialPorts = usbConnectionManager.getAvailablePorts()
        val adapter = CdcPortsAdapter(this, initialPorts)
        listView.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        // Keep the BroadcastReceiver setup here
        registerReceiver(usbCdcStateReceiver, IntentFilter(INTENT_ACTION_GRANT_USB))
    }

    override fun onStop() {
        super.onStop()
        // CRITICAL: Clean up resources when stopping
        usbConnectionManager.disconnect()
        unregisterReceiver(usbCdcStateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        // CRITICAL: Cancel all running coroutines to prevent leaks
        activityScope.cancel()
    }

    // --- UI Setup and Initialization ---

    private fun setupUI() {
        setSupportActionBar(findViewById(R.id.app_toolbar))
        
        connectionMessageView = findViewById(R.id.connection_msg)

        // Load preferences
        val prefs = getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
        val useDSlipProtocol = prefs.getBoolean(getString(R.string.protocol_type), true)
        val useDefaultSpeed = prefs.getBoolean(getString(R.string.speed_value), true)

        // Set typeface
        findViewById<TextView>(R.id.connection_msg).typeface = android.graphics.Typeface.MONOSPACE

        // Adapter setup (assuming CdcPortsAdapter constructor takes the list)
        val initialPorts = usbConnectionManager.getAvailablePorts()
        val adapter = CdcPortsAdapter(this, initialPorts)
        listView.adapter = adapter
        
        // Initialize the USB connection state based on preferences
        usbConnectionManager.setProtocolParams(useDSlipProtocol, useDefaultSpeed)
    }

    private fun setupListeners() {
        // Connection Button (Button ID: R.id.button)
        portButton = findViewById(R.id.button)
        portButton.setOnClickListener {
            // Launching connection logic in coroutine for non-blocking UI
            activityScope.launch {
                connectToDevice()
            }
        }

        // List View Click Listener
        listView.onItemClickListener = object : AdapterView.OnItemClickListener {
            override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Update selected state
                usbConnectionManager.setSelectedPort(position)
                Toast.makeText(this@MainActivity, "Selected Port Index: $position", Toast.LENGTH_SHORT).show()
            }
        }

        // Clear Button (Button ID: R.id.buttonClear)
        findViewById<Button>(R.id.buttonClear).setOnClickListener {
            connectionMessageView.text = ""
        }

        // Exchange Button (Button ID: R.id.buttonExchange)
        exchangeButton = findViewById(R.id.buttonExchange)
        exchangeButton.setOnClickListener {
            // Start the connection flow
            activityScope.launch {
                initiateConnectionExchange()
            }
        }
    }
    
    // --- Connection Logic Flow ---

    private suspend fun connectToDevice() = withContext(Dispatchers.IO) {
        try {
            // 1. Get available drivers and find the first one
            val manager = getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
            if (availableDrivers.isEmpty()) {
                connectionMessageView.text = getString(R.string.text_driver_unavailable)
                return@withContext
            }

            val driver = availableDrivers[0]
            
            // 2. Handle Permission Request
            var connection = manager.openDevice(driver.device)
            if (connection == null) {
                // Request permission flow remains largely the same
                connectionMessageView.text = getString(R.string.text_need_permission)
                // Implement the PendingIntent request here
                return@withContext
            }

            // 3. Process Ports and Update UI (Thread-safe data manipulation)
            val ports = processUsbPorts(driver, connection)
            usbConnectionManager.updateAvailablePorts(ports)
            
            // 4. Clean up connection resources (Close connection after reading ports)
            // This prevents resource deadlock issues if multiple drivers are present.
            connection.close()
            
        } catch (e: Exception) {
            connectionMessageView.append("Connection Error: ${e.message}\n")
        }
    }

    private fun processUsbPorts(driver: UsbSerialProber.Driver, connection: android.hardware.usb.UsbConnection): List<CdcPortData> {
        val ports = mutableListOf<CdcPortData>()
        
        for (port in driver.ports) {
            try {
                port.open(connection)
                // (Endpoint address logic remains the same)
                val writeEndpointAddr = if (port.writeEndpoint != null) port.writeEndpoint.address else 0
                val readEndpointAddr = if (port.readEndpoint != null) port.readEndpoint.address else 0
                ports.add(CdcPortData(port.portNumber, writeEndpointAddr, readEndpointAddr))
            } catch (e: Exception) {
                Log.e("USB", "Failed to process port: ${e.message}")
                // Log the error but continue iterating
                ports.add(CdcPortData(0, 0, 0))
            }
        }
        return ports
    }
    
    private suspend fun initiateConnectionExchange() {
        // Cancel previous data streams first
        dataStreamJob?.cancel()

        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            connectionMessageView.text = "No device found."
            return
        }

        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device) ?: run {
            connectionMessageView.text = "Failed to open USB connection."
            return
        }

        try {
            // 1. Disconnect/Close the previous port gracefully
            usbConnectionManager.disconnect()
            
            // 2. Get the selected port and open the connection
            val selectedPort = usbConnectionManager.getSelectedPort()
            if (selectedPort == null) {
                connectionMessageView.text = "Please select a valid port."
                return
            }
            
            val mPort = driver.ports[selectedPort]
            if (mPort == null) throw IllegalStateException("Selected port object is null.")
            
            mPort.open(connection)

            // 3. Configure and Start Data Stream (This now uses the service)
            val speed = if (usbConnectionManager.isUseDefaultSpeed) 115200 else 921600
            mPort.setParameters(speed, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // 4. Write the initial command
            val request = usbConnectionManager.getProtocolRequest()
            mPort.write(request, 0)
            
            connectionMessageView.text = getString(R.string.written)

            // 5. Start the background listening job
            dataStreamJob = activityScope.launch {
                usbConnectionManager.startDataStream(mPort)
            }
        } catch (e: Exception) {
            connectionMessageView.append("Connection/Streaming Error: ${e.message}\n")
        } finally {
            // Ensure the connection is closed regardless of success or failure
            connection.close()
        }
    }


    // --- Broadcast Receiver (Remains largely the same) ---

    private val usbCdcStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (INTENT_ACTION_GRANT_USB == intent.action) {
                val usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                if (usbPermission) {
                    Toast.makeText(context, "Granted", Toast.LENGTH_SHORT).show()
                    connectionMessageView.text = getString(R.string.try_one_more_time)
                } else {
                    Toast.makeText(context, "Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- Utility Extension Function (Kotlin Idiom) ---
    fun ByteArray.toHex(): String {
        val sb = StringBuilder()
        var i = 0
        while (i < this.size) {
            val untilValue = kotlin.math.min(this.size, i + 12)
            val range = this.slice(i until untilValue)
            val hexStr = range.joinToString(separator = " ") { "%02x".format(it) }
            sb.append(hexStr).append("\n")
            i += 12
        }
        return sb.toString()
    }
}
