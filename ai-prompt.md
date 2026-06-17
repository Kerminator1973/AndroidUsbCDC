# Попытка улучшить код приложения

Для улучшения моего кода, который очень далёк от совершенства, хочу попытаться использовать ИИ. Стартовый промпт для локальной Gemma 4:

```
I have provided several files (the attached project) that handle connection by USB between the mobile app and the Arduino-controller. The code currently works, but I suspect it is inefficient and lacks modern best practices.

My primary goals are: 
1. Improve performance/speed.
2. Increase readability and maintainability.
3. Add specific feature: robust concurency

Please act as a Senior Software Architect. Review all files and provide the following:
1. **Key Areas for Improvement:** Identify 3-5 specific places in the code that are inefficient (e.g., redundant database queries, nested loops, poor variable naming).
2. **Refactored Code:** Provide a fully refactored version of the files. Do not just fix the problems; show me how to make the *whole system* cleaner.
3. **Explanation:** For each major change you made, explain *why* it is an improvement (e.g., "I replaced the try/catch block with a dedicated result object to improve type safety.").

Use separate code blocks for the suggested refactoring. Keep the explanation concise but highly technical.
```

Все четыре файла с исходными текстами проекта были переданы Gemma 4 для анализа.

Результат Gemma 4:

As a Senior Software Architect, I've reviewed your codebase. The current structure works but exhibits several signs of tight coupling, manual thread management (leading to complex concurrency handling), and reliance on mutable state, which hinders scalability and robustness.

The primary area needing overhaul is the communication and resource management layer (USB connection and data streaming).

** Key Areas for Improvement (Architectural Critique)**

I have identified 4 critical areas that require architectural refactoring:

- Concurrency and State Management (The biggest issue): The current flow mixes UI updates, IO operations (USB I/O), and background polling (the commented-out Executors section) within the onCreate method's single OnClickListener. This violates clean architecture principles and makes robust concurrent data handling virtually impossible to guarantee.
- Resource and Lifecycle Management: The manual calling of mPort?.close() and the lack of dedicated cleanup logic (e.g., using Kotlin's use block or proper coroutine scope cancellation) risks resource leaks, especially when the user navigates away from the screen or multiple connections are attempted.
- Data Access Layer Mixing: The MainActivity is responsible for UI logic, USB connection logic, reading preferences, and managing the data stream listener (SerialInputOutputManager). This high degree of coupling makes the code difficult to test and maintain.
- Efficiency in Adapter (ViewHolder Pattern): The CdcPortsAdapter implementation ignores the modern Android ViewHolder pattern. Instead, it relies on findViewById and assumes the convertView structure, which is an outdated and prone-to-errors approach in modern Android development.

### Комментарий по "ключевым улучшениям", предложенным Gemma 4

Согласен с нарушением Clean Code. Действительно нужно выделить работу с прибором по USB в отдельный класс.

Без использования **ViewHolder** в адаптере списка при каждой отрисовке элемента:

- Вызывается метод findViewById() для поиска дочерних View внутри элемента списка
- Происходит раздувание (inflation) макета для каждого нового элемента

Эти операции требуют значительных ресурсов CPU, особенно в длинных списках, что приводит к:

- "лагающей" прокрутке
- повышенному расходу батареи
- увеличению нагрузки на процессор

Действительно, в моём коде не используется ViewHolder и для каждого из элементов используется findViewById():

```java
override fun getView(position: Int, reusedConvertView: View?, parent: ViewGroup): View? {
    val convertView : View? = reusedConvertView ?: LayoutInflater.from(context).inflate(R.layout.row, parent, false)
    if (null != convertView) {
        idNumber = convertView.findViewById(R.id.idNumber)
        idNumber.text = arrayList[position].getId().toString()

        writeEndpoint = convertView.findViewById(R.id.writeEndpoint)
        writeEndpoint.text = "Write Endpoint: " + arrayList[position].getWriteEndpoint()

        readEndpoint = convertView.findViewById(R.id.readEndpoint)
        readEndpoint.text = "Read Endpoint: " + arrayList[position].getReadEndpoint()
    }

    return convertView
}
```

Кажется, что CdcPortsAdapter один из главных кандидатов на замену. Изменение на даст сильного улучшения в производительности (список очень маленький), но в качестве шаблона для заимствования - прекрасная иллюстрация.

## Предложения по рефакторингу кода

**Refactored Code**

To implement robust concurrency and improve separation of concerns, I will introduce a dedicated Repository/Service layer (e.g., UsbConnectionManager) and modernize the UI state management using Kotlin Coroutines and Flow for handling asynchronous data streams.

1. MainActivity.kt (Refactored)

This file is simplified significantly to focus only on UI setup, coordinating state, and handling lifecycle events, delegating all complex logic to the service layer.

```kotlin
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
```

>При сборке возникло множество ошибок, которые требуется устранить. Причём ошибки связаны с отсутствием определений в реализации "UsbConnectionManager.kt". Две попытки устранить ошибки - были неуспешными. Кажется, что проще и более правильно - вручную переписать код.
>
>Оценка качестве генерации кода посредством Gemma 4 - в целом здравые идеи, но LLM галлюционировала при создании кода.

**UsbConnectionManager.kt (NEW CORE SERVICE LAYER)**

This service handles all complex USB logic, concurrency, and state management, completely separating it from the Activity.

```kt
package ru.dors.androidusbcdc

import android.content.Context
import android.util.Log
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
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
    private var currentConnection: android.hardware.usb.UsbConnection? = null
    
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
        var connection: android.hardware.usb.UsbConnection? = null
        
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
    suspend fun connect(driver: UsbSerialProber.Driver): Boolean = withContext(Dispatchers.IO) {
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
                (context as? MainActivity)?.let { activity ->
                    // We pass the data back to the activity to handle the TextView update
                    activity.handleIncomingData(data.toHex()) 
                }
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
```

При попытке компиляции кода возник ряд ошибок, в частности:

- В android.hardware.usb отсутсвует класс UsbConnection, но есть **UsbDeviceConnection**, который близок по смыслу. В библиотеке **android.hardware.usb** также есть такие классы, как: UsbManager, UsbDevice, UsbAccessory, UsbInterface, UsbEndpoint и UsbRequest. Замечу, что я использую часть этих классов, в частности UsbManager, для получения прав на использование приложением USB интерефейса
- Также код использует UsbSerialProber.Driver. В библиотеке [mik3y](https://github.com/mik3y/usb-serial-for-android) есть просто UsbSerialDriver

Также компилятору не понравился вот этот код:

```kt
override fun onNewData(data: ByteArray) {
    // This callback is executed on the background thread managed by the library.
    // We must launch a coroutine on the Main Dispatcher to update the UI.
    // In a ViewModel context, this data would be emitted via a Flow.
    (context as? MainActivity)?.let { activity ->
        // We pass the data back to the activity to handle the TextView update
        activity.handleIncomingData(data.toHex())
    }
}
```

Описание ошибки: "Cannot infer type for this parameter. Specify it explicitly".

Решение первой проблемы:

```kt
val activity = context as? MainActivity ?: return
```

До момента, пока не изменён интерфейс главного Activity приложения, закомментировал отправку результатов работы:

```kt
// TODO: посылать данные в главное окно
//activity.handleIncomingData(data.toHex())
```

Файл стал компилироваться, но его использование требует значительной переработки MainActivity.

Что касается реализации, то Gemma 4 использует в коде Kotlin Coroutines, в частности объекты MutableStateFlow и StateFlow.

Состояние класса - изменяемый список портов и текущий выбранный порт. Они предназначены для использования внешним кодом, в частности - "MainActivity.kt":

```java
private val _availablePorts = MutableStateFlow<List<CdcPortData>>(emptyList())
val availablePorts: StateFlow<List<CdcPortData>> = _availablePorts.asStateFlow()

// State for the selected port index
private val _selectedPortIndex = MutableStateFlow<Int?>(null)
val selectedPortIndex: StateFlow<Int?> = _selectedPortIndex.asStateFlow()
```

Основные характеристики **StateFlow**:

- Хранит текущее значение. StateFlow всегда содержит последнее установленное значение, доступное через свойство value
- Передаёт обновления. Автоматически уведомляет подписчиков о любых изменениях состояния
- Гарантирует получение актуального состояния. Новые подписчики сразу получают текущее значение и все последующие обновления
- Является «горячим» потоком. Работает независимо от наличия подписчиков: не останавливается при отсутствии наблюдателей и не перезапускается при появлении новых
- Передаёт только изменения. Если новое значение идентично текущему, обновления не отправляются подписчикам

Использование в коде должно выглядеть следующим образом (подписчики):

```java
lifecycleScope.launch {
    stateFlow.collect { newValue ->
        println("Новое значение: $newValue")
    }
}
```

Следует заметить, что функционал связан с lifecycleScope. **lifecycleScope** — это предопределённая область корутин (CoroutineScope) из библиотеки AndroidX Lifecycle, которая автоматически привязана к жизненному циклу компонента Android (например, Activity, Fragment или LifecycleOwner). Разберём подробно.

Её необходимо добавить в проект:

```gradle
implementation "androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version"
```

**CdcPortsAdapter.kt (Refactored: Incorporating ViewHolders)**

This updates the adapter to use the highly recommended ViewHolder pattern, which significantly improves performance by avoiding repeated findViewById calls during scrolling/binding.

```kt
package ru.dors.androidusbcdc

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.view.isVisible

/**
 * Implements the ViewHolder pattern for efficient ListView rendering.
 */
class CdcPortsAdapter(private val context: Context, private val arrayList: java.util.ArrayList<CdcPortData>) : BaseAdapter() {

    // Inner class to hold view references, replacing findViewById calls
    private class ViewHolder(view: View) {
        val idNumber: TextView = view.findViewById(R.id.idNumber)
        val writeEndpoint: TextView = view.findViewById(R.id.writeEndpoint)
        val readEndpoint: TextView = view.findViewById(R.id.readEndpoint)
    }

    override fun getCount(): Int = arrayList.size
    override fun getItem(position: Int): Any = position
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        val view: View
        var viewHolder: ViewHolder? = null

        if (convertView == null) {
            // Create View and ViewHolder if null (first time inflation)
            view = LayoutInflater.from(context).inflate(R.layout.row, parent, false)
            viewHolder = ViewHolder(view)
            // Tag the view with the ViewHolder instance for fast retrieval later
            (view as ViewGroup).tag = viewHolder
        } else {
            // Recycle and reuse the existing View
            view = convertView
            viewHolder = (view as ViewGroup).tag as ViewHolder
        }

        // Use the ViewHolder references for data binding
        val portData = arrayList[position]
        viewHolder?.apply {
            idNumber.text = portData.getId().toString()
            writeEndpoint.text = "Write Endpoint: ${portData.getWriteEndpoint()}"
            readEndpoint.text = "Read Endpoint: ${portData.getReadEndpoint()}"
        }

        return view
    }
}
```

**CdcPortData.kt (Minor Refactoring)**

This class remains simple but adopts modern Kotlin properties, making it a cleaner data container.

```kt
package ru.dors.androidusbcdc

/**
 * Data class replacement for CdcPortData. Improves type safety and immutability.
 * Note: Using a data class makes it automatically comparable and copyable.
 */
data class CdcPortData(
    val id: Int,
    val writeEndpoint: Int,
    val readEndpoint: Int
)
```

>Оригинальный подход состоял в том, что реализуется класс с getter-ами, т.е. значения нельзя изменить после того, как экземпляр класса был создан.
>
>Gemma 4 предложила  сделать все поля val-ами, т.е. неизменными значениями и это позволяет достичь двух целей:
>
>- неизменность поля определена на уровне синтаксиса языка, а не семантики, как в моём коде
>- предложенный код проще, легче читается

Что за ключевое слово **data**?

This is one of the most useful and idiomatic features of Kotlin!

In short, the keyword data acts as a design marker that tells the Kotlin compiler: "This class is primarily designed to hold data, and I don't want to write the tedious boilerplate code for basic data functionality."

When you declare a class using data, the Kotlin compiler automatically generates (implements) several useful methods and functions for you that you would otherwise have to write out manually in a regular class.

**What data Automatically Generates (The Magic)**

For your example:

```kt
data class CdcPortData(
    val id: Int,
    val writeEndpoint: Int,
    val readEndpoint: Int
)
```

If you had used class instead of data class, it would compile, but it would behave differently when you tried to compare or copy instances.

By using data, you instantly get the following built-in features:

1. equals() and hashCode() (The Biggest Benefit)

This is the most critical feature. By default, if you write a regular class, two instances are only considered equal if they point to the exact same memory location (like comparing two variable names).

With data class, Kotlin makes equals() and hashCode() compare the values of all the properties.

Example: If you create two CdcPortData objects, even if they are separate instances in memory, if they have the same id, writeEndpoint, and readEndpoint, the compiler guarantees that instanceA == instanceB will evaluate to true. This makes them behave correctly when used in collections like Set or as keys in Map.

2. toString()

When you print a data class instance, it doesn't just show a memory address (like com.example.CdcPortData@1234). Instead, it shows a clean, readable representation of its contents:

Result: CdcPortData(id=1, writeEndpoint=2, readEndpoint=3)

3. copy()

This is extremely useful when you need to create a slightly modified version of an existing object without changing the original object (this is called immutability).

Example: If you have an object and just want to change the writeEndpoint, you can use copy():

```java
val original = CdcPortData(1, 2, 3)
// Create a new object that copies everything from 'original' 
// but changes the writeEndpoint to 99
val updated = original.copy(writeEndpoint = 99) 
```

4. Component Functions (Destructuring)

Data classes allow you to easily "destructure" an object, meaning you can pull its properties out into individual variables cleanly.

Example:

```java
val myData = CdcPortData(4, 5, 6)
val (id, write, read) = myData // Deconstructs the object
// Now 'id' is 4, 'write' is 5, etc.
```

>Да, ключевое слово **data** выглядит очень интересным, но в конкретно моём случае, кажется, что добавление в класс нескольких дополнительных функций и методов является избыточным. На первый взгляд, в моём коде смысла в использовании этого модификатора нет.

**OptionsActivity.kt (Minor Refactoring)**

The logic is cleaner by using standard delegation and modern Kotlin syntax.

```kt
package ru.dors.androidusbcdc

import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        // Standard Toolbar setup (remains the same)
        setSupportActionBar(findViewById(R.id.back_toolbar"))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Simplified initialization using Kotlin apply/let chain
        val prefs = getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
        
        // Protocol Group Listener
        val protocolGroup = findViewById<RadioGroup>(R.id.protocolGroup)
        protocolGroup.setOnCheckedChangeListener { _, checkedId ->
            val isDslip = checkedId == R.id.dslip_button
            updatePreference(prefs, "protocol_type", isDslip)
        }

        // Speed Group Listener
        val speedGroup = findViewById<RadioGroup>(R.id.speedGroup)
        speedGroup.setOnCheckedChangeListener { _, checkedId ->
            val isDefaultSpeed = checkedId == R.id.default_speed_button
            updatePreference(prefs, "speed_value", isDefaultSpeed)
        }

        // Initialize Check States
        // We set the default selection based on current preferences
        fun setChecked(group: RadioGroup, checkedId: Int) {
             findViewById<RadioButton>(checkedId)?.isChecked = true
        }

        setChecked(findViewById(R.id.protocolGroup), if (prefs.getBoolean(getString(R.string.protocol_type), true)) R.id.dslip_button else R.id.ccnet_button)
        setChecked(findViewById(R.id.speedGroup), if (prefs.getBoolean(getString(R.string.speed_value), true)) R.id.default_speed_button else R.id.megabit_speed_button)
    }
    
    /**
     * Helper function to simplify preference writing logic.
     */
    private fun updatePreference(prefs: android.content.SharedPreferences, key: String, value: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }
}
```

**Explanation of Architectural Changes**

1. Introducing UsbConnectionManager (Separation of Concerns & Testability)

Why: The original MainActivity was a God Object, mixing UI logic, USB IO, threading, and preference management. By extracting all connection state, logic, and IO into UsbConnectionManager, we achieve strict Separation of Concerns.

Improvement: The MainActivity now acts purely as a View/Controller, delegating all what and how of USB communication to the manager. This makes the codebase significantly more testable and easier to debug.

2. Adopting Kotlin Coroutines and StateFlow (Robust Concurrency)

Why: The original approach relied on manual threading (implicitly via Runnables or commented-out Executors), which is notoriously difficult to manage, especially regarding lifecycle events and resource cancellation.

Improvement:
CoroutineScope & Job: We use CoroutineScope(Dispatchers.Main + Job()) in MainActivity and activityScope.cancel() in onDestroy(). This implements structured concurrency, guaranteeing that all background tasks associated with the UI are canceled automatically when the Activity is destroyed, preventing memory leaks and crashes.

StateFlow: Instead of manually notifying the UI (e.g., adapter!!.notifyDataSetChanged()), the UsbConnectionManager exposes state using StateFlow. This is the modern, reactive way to handle mutable state in Kotlin, making the MainActivity observer-based rather than imperative.

3. Implementation of ViewHolder Pattern (CdcPortsAdapter)

Why: The original adapter used basic inflation and assumed resource existence, leading to performance bottlenecks and potential NullPointerExceptions when recycling views.

Improvement: The ViewHolder pattern is the standard Android best practice for high-performance ListView adapters. By storing view references in a ViewHolder object attached to the view tag, we eliminate repeated findViewById calls, drastically reducing memory overhead and improving the UI rendering speed.

4. Immutability and Data Modeling (CdcPortData Data Class)

Why: Using simple classes for data structures makes them mutable by default. When dealing with state that must be read and passed across different threads (like port configurations), immutability is crucial for thread safety.

Improvement: Converting CdcPortData to a Kotlin data class ensures that instances are designed to be immutable, which is a foundational step toward writing predictable, thread-safe concurrent code.

5. Resource Management via try-finally and disconnect()

Why: The original code had ambiguous points regarding when and how ports and connections were closed (TODO: when to close port?). Poor resource management leads to resource leaks (USB handles).

Improvement: We formalized the connection lifecycle:
All connection logic now wraps resource opening/closing in try-finally blocks.
UsbConnectionManager.disconnect() is explicitly called in onStop() and at the start of any new connection attempt (initiateConnectionExchange), ensuring that regardless of how the flow exits, all USB handles are properly released.

## Попытка сгенерировать работоспособный код используя Claude Sonnet 4.6 Thinking

Промпт:

```
Я прикрепил несколько файлов Android-приложения, которые осуществляют обмен данными по USB между приложением и контроллером Arduino. Код работает, но он нуждается в улучшении и применении современных лучших практик.

Мои основные цели:

1. Повысить читаемость и упростить сопровождение кода
2. Отделить пользовательский интерфейс от кода обменна данными по USB. Класс, включающий работу по USB должен называться "UsbConnectionManager.kt"
3. Для реализации многопоточности следует использовать Kotlin Coroutines

Действуй как Senior Software Architect. Выполни анализ всех файлов и предложи следующее:

1. **Key Areas for Improvement:** Identify 3-5 specific places in the code that are inefficient (e.g., redundant database queries, nested loops, poor variable naming).
2. **Refactored Code:** Provide a fully refactored version of the files. Do not just fix the problems; show me how to make the *whole system* cleaner.

Use separate code blocks for the suggested refactoring. Keep the explanation concise but highly technical.
```

>Следует заметить, что для Claude Sonnet задача оказалась очень не простой и её решение заняло несколько минут. Стоимость генерации кода - около 100 рублей.

Key Areas for Improvement

1. God Activity — Blatant SRP Violation

All USB logic (driver discovery, permission requests, port scanning, parameter configuration, sending frames, and receiving data) is embedded inside anonymous View.OnClickListener objects directly in onCreate(). MainActivity simultaneously manages the view hierarchy, serial port parameters, and data formatting.

2. Critical Logic Bug: SerialInputOutputManager is stopped on the same line it starts

```kt
serialInputOutputManager!!.start()
// ... a few lines later, synchronously:
serialInputOutputManager?.stop() // kills the read loop immediately — onNewData is dead code
```

3. Redundant USB driver enumeration

UsbSerialProber.getDefaultProber().findAllDrivers(manager as UsbManager?) is called identically in two button handlers with no caching. Each call performs a full USB bus scan.

4. Java-style SAM anonymous classes in Kotlin

```kt
setOnClickListener(object : View.OnClickListener { override fun onClick(v: View?) { ... } }) — Kotlin SAM interfaces collapse to a simple lambda.
```

5. No single source of truth for preferences

```kt
Magic string "USB_CDC_PREFS" and getString(R.string.protocol_type/speed_value) are duplicated across two Activity files. getSharedPreferences() is also called multiple times within the same method in OptionsActivity. Additionally, CdcPortsAdapter.getItem() returns position (an Int) instead of the actual CdcPortData object.
```

### Refactored Code

CdcPortData.kt — unchanged, already idiomatic.

### Extensions.kt (new)

Оригинальный код выглядел следующим образом:

```kt
fun ByteArray.toHex(): String {

    val sb = StringBuilder()
    for (i in 0..this.size step 12) {

        val untilValue : Int = kotlin.math.min(this.size, i + 12)
        val range = this.slice(i until untilValue)
        val hexStr = range.joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }
        sb.append(hexStr)
        sb.append("\n")
    }
```

После одной неудачной попытки, Claude Sonnet 4.6 переписал код так:


```kt
fun ByteArray.toHex(): String = buildString {
    val bytes = this@toHex
    for (i in 0 until bytes.size step 12) {
        val chunk = bytes.slice(i until minOf(bytes.size, i + 12))
        append(chunk.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) })
        append('\n')
    }
}
```

Т.е. он сделал slice и избежал использования `kotlin.math`. Отлично! Это минорный, но полезный рефакторинг.

### AppPreferences.kt (new)

```kt
package ru.dors.androidusbcdc

import android.content.Context
import android.content.SharedPreferences

/**
 * Single source of truth for all persistent application preferences.
 * Eliminates the "USB_CDC_PREFS" magic string and duplicate getSharedPreferences()
 * calls scattered across multiple Activities.
 *
 * Key values intentionally match the original R.string resource values to remain
 * backward-compatible with preferences saved by previous app versions.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var useDSlipProtocol: Boolean
        get() = prefs.getBoolean(KEY_PROTOCOL, DEFAULT_USE_DSLIP)
        set(value) = prefs.edit().putBoolean(KEY_PROTOCOL, value).apply()

    var useDefaultSpeed: Boolean
        get() = prefs.getBoolean(KEY_SPEED, DEFAULT_USE_DEFAULT_SPEED)
        set(value) = prefs.edit().putBoolean(KEY_SPEED, value).apply()

    companion object {
        private const val PREFS_NAME             = "USB_CDC_PREFS"
        const val KEY_PROTOCOL                   = "protocol_type"
        const val KEY_SPEED                      = "speed_value"
        private const val DEFAULT_USE_DSLIP      = true   // DSlip
        private const val DEFAULT_USE_DEFAULT_SPEED = true   // 115 200 baud
    }
}
```

### UsbConnectionManager.kt (new)

```kt
package ru.dors.androidusbcdc

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Manages the full lifecycle of a USB CDC serial connection.
 *
 * Responsibilities:
 *  - Discovering USB serial drivers and enumerating their ports
 *  - Opening/closing the serial port and configuring line parameters
 *  - Bridging the callback-based [SerialInputOutputManager] to Kotlin [SharedFlow]
 *  - Sending raw byte frames to the connected device
 *
 * Threading model: blocking USB I/O is dispatched to [Dispatchers.IO] via suspend
 * functions. Callbacks from [SerialInputOutputManager] use [MutableSharedFlow.tryEmit],
 * which is thread-safe and non-blocking — no internal CoroutineScope required.
 *
 * The owner (Activity / ViewModel) must call [release] when done.
 */
class UsbConnectionManager(private val usbManager: UsbManager) {

    // ---- Public streams -------------------------------------------------------

    /** Emits every raw byte frame received from the USB device. */
    private val _incomingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    /** Emits human-readable error descriptions for the UI layer to surface. */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // ---- Private state -------------------------------------------------------

    private var activePort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    // ---- Public API ----------------------------------------------------------

    /**
     * Returns the first USB serial device detected, or null if none is attached.
     * Callers should verify [UsbManager.hasPermission] on the result before
     * proceeding to [scanPorts] or [connect].
     */
    fun findDevice(): UsbDevice? =
        UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .firstOrNull()
            ?.device

    /**
     * Enumerates all CDC serial ports exposed by the first connected device.
     *
     * Note: closing any [UsbSerialPort] also closes the shared
     * [android.hardware.usb.UsbDeviceConnection]. To avoid breaking
     * mid-enumeration on multi-port devices (e.g. Raspberry Pi Pico with
     * REPL + CDC ports), only the first port is closed after the scan completes.
     */
    suspend fun scanPorts(): List<CdcPortData> = withContext(Dispatchers.IO) {
        val driver = UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager).firstOrNull()
            ?: return@withContext emptyList()

        val connection = usbManager.openDevice(driver.device)
            ?: return@withContext emptyList()

        val ports = driver.ports.map { port ->
            runCatching {
                port.open(connection)
                CdcPortData(
                    id            = port.portNumber,
                    writeEndpoint = port.writeEndpoint?.address ?: 0,
                    readEndpoint  = port.readEndpoint?.address  ?: 0
                )
            }.getOrDefault(CdcPortData(id = 0, writeEndpoint = 0, readEndpoint = 0))
        }

        // Closing the first port closes the shared UsbDeviceConnection
        driver.ports.firstOrNull()?.runCatching { close() }
        ports
    }

    /**
     * Opens the USB serial port at [portIndex], configures line parameters,
     * and starts the background read loop.
     *
     * @param portIndex    Zero-based index into the list returned by [scanPorts].
     * @param useHighSpeed If true — 921 600 baud; otherwise 115 200 baud.
     * @return true on success; false if the device could not be opened
     *         (e.g. permission was revoked between scan and connect).
     */
    suspend fun connect(portIndex: Int, useHighSpeed: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val driver = UsbSerialProber.getDefaultProber()
                .findAllDrivers(usbManager).firstOrNull()
                ?: return@withContext false

            val connection = usbManager.openDevice(driver.device)
                ?: return@withContext false

            disconnect() // Stop any existing IO manager and close the previous port

            val port = driver.ports[portIndex]
            activePort = port

            try {
                val baudRate = if (useHighSpeed) BAUD_RATE_HIGH else BAUD_RATE_DEFAULT
                port.open(connection)
                port.setParameters(baudRate, DATA_BITS, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                port.dtr = true  // Data Terminal Ready: signals the host is ready
                port.rts = true  // Request To Send: required for Arduino/Pico to begin exchange
                startListening()
                true
            } catch (e: Exception) {
                _errors.tryEmit("Connection failed: ${e.message}")
                false
            }
        }

    /**
     * Sends a raw byte frame to the connected device.
     *
     * @return [Result.success] wrapping bytes written, or [Result.failure] with
     *         the underlying exception (including if no port is active).
     */
    suspend fun send(data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            checkNotNull(activePort) { "No active port — call connect() first." }
                .write(data, WRITE_TIMEOUT_MS)
            Unit
        }
    }

    /** Stops the IO read loop and closes the active serial port. */
    fun disconnect() {
        ioManager?.stop()
        ioManager = null
        activePort?.runCatching { close() }
        activePort = null
    }

    /** Convenience alias — must be called when the owning component is destroyed. */
    fun release() = disconnect()

    // ---- Private helpers -----------------------------------------------------

    private fun startListening() {
        val port = activePort ?: return
        ioManager = SerialInputOutputManager(
            port,
            object : SerialInputOutputManager.Listener {
                // tryEmit is thread-safe and non-blocking: no coroutine launch needed
                override fun onNewData(data: ByteArray) {
                    _incomingData.tryEmit(data)
                }
                override fun onRunError(e: Exception) {
                    _errors.tryEmit("IO error: ${e.message}")
                }
            }
        ).also {
            it.readTimeout = 0
            it.start()
        }
    }

    private companion object {
        const val BAUD_RATE_DEFAULT = 115_200
        const val BAUD_RATE_HIGH    = 921_600
        const val DATA_BITS         = 8
        const val WRITE_TIMEOUT_MS  = 1_000
    }
}
```

### MainActivity.kt

```kt
package ru.dors.androidusbcdc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView.OnItemClickListener
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // ---- Dependencies --------------------------------------------------------
    private lateinit var prefs: AppPreferences
    private lateinit var usbManager: UsbManager
    private lateinit var connectionManager: UsbConnectionManager

    // ---- UI references (cached once after setContentView) --------------------
    private lateinit var connectionMsgView: TextView
    private lateinit var devicePidVidView: TextView
    private lateinit var deviceNameView: TextView
    private lateinit var portsAdapter: CdcPortsAdapter

    // ---- State ---------------------------------------------------------------
    private val portList = mutableListOf<CdcPortData>()
    private var selectedPortIndex = 0

    // Computed lazily so that packageName (available after attach) is safe to read
    private val actionGrantUsb by lazy { "$packageName.GRANT_USB" }

    // ---- Lifecycle -----------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.app_toolbar))

        prefs          = AppPreferences(this)
        usbManager     = getSystemService(USB_SERVICE) as UsbManager
        connectionManager = UsbConnectionManager(usbManager)

        bindViews()
        setupPortsList()
        setupButtonListeners()
        observeConnectionManager()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, usbPermissionReceiver,
            IntentFilter(actionGrantUsb),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(usbPermissionReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager.release()
    }

    // ---- Options menu --------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, OptionsActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---- Private setup -------------------------------------------------------

    private fun bindViews() {
        connectionMsgView = findViewById<TextView>(R.id.connection_msg).also {
            it.typeface = Typeface.MONOSPACE
        }
        devicePidVidView = findViewById(R.id.textViewDevice)
        deviceNameView   = findViewById(R.id.textViewIdentification)
    }

    private fun setupPortsList() {
        portsAdapter = CdcPortsAdapter(this, portList)
        findViewById<ListView>(R.id.listView).apply {
            adapter = portsAdapter
            onItemClickListener = OnItemClickListener { _, _, index, _ ->
                selectedPortIndex = index
                Toast.makeText(applicationContext, index.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtonListeners() {
        findViewById<Button>(R.id.button).setOnClickListener         { onScanClicked() }
        findViewById<Button>(R.id.buttonExchange).setOnClickListener { onExchangeClicked() }
        findViewById<Button>(R.id.buttonClear).setOnClickListener    { connectionMsgView.text = "" }
    }

    /**
     * Collects [UsbConnectionManager.incomingData] and [UsbConnectionManager.errors].
     * [repeatOnLifecycle] automatically suspends collection when the Activity goes below
     * STARTED (screen off / backgrounded) and resumes it on return to foreground,
     * preventing unnecessary work and background thread wakeups.
     */
    private fun observeConnectionManager() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    connectionManager.incomingData.collect { data ->
                        connectionMsgView.append(data.toHex())
                    }
                }
                launch {
                    connectionManager.errors.collect { error ->
                        connectionMsgView.append("Error: $error\n")
                    }
                }
            }
        }
    }

    // ---- Button handlers -----------------------------------------------------

    private fun onScanClicked() {
        val device = connectionManager.findDevice()
        if (device == null) {
            connectionMsgView.text = getString(R.string.text_driver_unavailable)
            return
        }
        if (!usbManager.hasPermission(device)) {
            connectionMsgView.text = getString(R.string.text_need_permission)
            requestUsbPermission(device)
            return
        }

        devicePidVidView.text = "pid = ${device.productId}, vid = ${device.vendorId}"
        deviceNameView.text   = device.deviceName

        lifecycleScope.launch {
            val ports = connectionManager.scanPorts()
            portList.clear()
            portList.addAll(ports)
            portsAdapter.notifyDataSetChanged()
        }
    }

    private fun onExchangeClicked() {
        lifecycleScope.launch {
            val connected = connectionManager.connect(
                portIndex    = selectedPortIndex,
                useHighSpeed = !prefs.useDefaultSpeed
            )
            if (!connected) {
                connectionMsgView.text = getString(R.string.text_exception)
                return@launch
            }

            connectionManager.send(buildCommand())
                .onSuccess {
                    connectionMsgView.text = getString(
                        R.string.connection_message_template,
                        selectedPortIndex.toString()
                    )
                }
                .onFailure {
                    connectionMsgView.append("Exception during write command\n")
                }
        }
    }

    // ---- Private helpers -----------------------------------------------------

    /** Assembles the protocol-specific identification command frame. */
    private fun buildCommand(): ByteArray = if (prefs.useDSlipProtocol) {
        byteArrayOf(0xB4.toByte(), 0x00, 0x81.toByte(), 0x00, 0x74)       // DSlip: identification
    } else {
        byteArrayOf(0x02, 0x03, 0x06, 0x37, 0xFE.toByte(), 0xC7.toByte()) // CCNet: identification
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val intent = Intent(actionGrantUsb).apply { setPackage(packageName) }
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, pendingIntent)
    }

    // ---- USB permission receiver ---------------------------------------------

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != actionGrantUsb) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            connectionMsgView.text = getString(
                if (granted) R.string.try_one_more_time else R.string.text_need_permission
            )
            Toast.makeText(
                this@MainActivity,
                if (granted) "Granted" else "Denied",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
```

### OptionsActivity.kt

```kt
package ru.dors.androidusbcdc

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        setSupportActionBar(findViewById(R.id.back_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = AppPreferences(this)

        initProtocolGroup()
        initSpeedGroup()
    }

    private fun initProtocolGroup() {
        val buttonId = if (prefs.useDSlipProtocol) R.id.dslip_button else R.id.ccnet_button
        findViewById<RadioButton>(buttonId).isChecked = true

        findViewById<RadioGroup>(R.id.protocolGroup).setOnCheckedChangeListener { _, checkedId ->
            prefs.useDSlipProtocol = (checkedId == R.id.dslip_button)
        }
    }

    private fun initSpeedGroup() {
        val buttonId = if (prefs.useDefaultSpeed) R.id.default_speed_button else R.id.megabit_speed_button
        findViewById<RadioButton>(buttonId).isChecked = true

        findViewById<RadioGroup>(R.id.speedGroup).setOnCheckedChangeListener { _, checkedId ->
            prefs.useDefaultSpeed = (checkedId == R.id.default_speed_button)
        }
    }
}
```

>Добавление "AppPreferences.kt" и рефакторинг "OptionsActivity.kt" выглядить вполне разумным. Экспресс-проверка - код работоспособен.

### Architectural Summary

| Before                                      | After                                        |
|---------------------------------------------|----------------------------------------------|
| USB logic in anonymous click listeners      | Isolated in UsbConnectionManager             |
| runOnUiThread {} callbacks                  | SharedFlow collected via lifecycleScope      |
| SerialInputOutputManager killed immediately | Runs continuously; stopped only in onDestroy |
| Magic strings duplicated across 2 files     | Single AppPreferences with typed properties  |
| ByteArray.toHex() inside MainActivity       | Top-level in Extensions.kt                   |
| java.util.ArrayList in adapter              | MutableList                                  |

tryEmit() is used in the SerialInputOutputManager callbacks instead of scope.launch { emit() } — it is thread-safe, non-blocking, and eliminates the need for an internal CoroutineScope inside UsbConnectionManager entirely.

## Следующая попытка оценки архитектуры приложения

Key Areas for Improvement (3-5 Points):

- **Concurrency Management and Threading Model**: The most critical issue is the inconsistent use of runOnUiThread and manual handling of callbacks (onDataReceived, onError). The data reception path is synchronous within the UsbConnectionManager but pushes UI updates in MainActivity. This violates modern Android best practices (which favor LiveData or Kotlin Flows) and makes managing background operations risky
- **Tight Coupling and Dependency Injection**: Classes like MainActivity and OptionsActivity hard-initialize singletons/services (AppPreferences, UsbConnectionManager) using findViewById() and direct calls to system services. This creates extreme coupling, making testing difficult and maintenance brittle
- **Resource Leak Management (State Handling)**: The UsbConnectionManager handles complex resource opening (mPort?.open(connection)) but does not guarantee clean shutdown across all failure paths. Furthermore, the reliance on setting up system receivers in onStart() and tearing them down only in onStop() can lead to subtle leaks or race conditions if other lifecycle methods are involved.
- **API Usage (View Binding/Kotlin Idioms)**: The code frequently uses manual view lookups (`findViewById<TextView>(R.id.connection_msg)`), which is boilerplate and error-prone in modern Android development. Using View Binding would vastly improve safety and readability.
- **Error Handling**: Many try/catch blocks (e.g., in getAvailablePorts, handleConnectionAttempt) catch the generic Exception. This swallows important context, making debugging impossible. Specific exception handling is mandatory for reliable systems.


Предложение по модификации "UsbConnectionManager.kt":

```kotlin
package ru.dors.androidusbcdc

import android.content.Context
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException

/**
 * Manages low-level USB CDC connection and data stream.
 * It now exposes a SharedFlow for data, making the class observable (reactive).
 */
class UsbConnectionManager(private val context: Context) {

    // Use Flows/Channels for reactive streaming of events instead of callbacks.
    companion object {
        const val TAG = "UsbService"
        private val _dataChannel = MutableSharedFlow<String>(extraBufferCapacity = 10)
        val dataChannel: SharedFlow<String> = _dataChannel

        private val _errorChannel = MutableSharedFlow<String>()
        val errorChannel: SharedFlow<String> = _errorChannel
    }

    // Private state variables
    @Volatile private var mPort: UsbSerialPort? = null
    private var serialInputOutputManager: SerialInputOutputManager? = null
    
    var currentSelectedPortIndex: Int = 0
        private set // Restrict external modification of port index

    // --- Public methods using structured concurrency safety checks ---

    /**
     * Retrieves a list of available endpoints. Remains similar, but improves error handling.
     */
    fun getAvailablePorts(manager: android.hardware.usb.UsbManager, driver: UsbSerialDriver): List<CdcPortData> {
        val portList = mutableListOf<CdcPortData>()
        for (port in driver.ports) {
            try {
                // Check if connection is possible before adding to list
                if (manager.hasPermission(driver.device)) { 
                    val connection = manager.openDevice(driver.device)
                    port.open(connection) // Opening here might fail, but we assume it's the best effort for discovery
                    
                    var writeEndpointAddr = port.writeEndpoint?.address ?: 0
                    var readEndpointAddr = port.readEndpoint?.address ?: 0

                    portList.add(CdcPortData(port.portNumber, writeEndpointAddr, readEndpointAddr))
                } else {
                    // Handle lack of permission gracefully for discovery list
                    portList.add(CdcPortData(port.portNumber, -1, -1)) 
                }
            } catch (e: IOException) {
                // Log the specific failure instead of swallowing it
                android.util.Log.w("UsbService", "Failed to process port ${port.portNumber}", e)
                // Only add if we couldn't fail spectacularly (return 0,0,0 would be misleading)
            }
        }
        return portList
    }

    /**
     * Establishes connection and initializes resource handles.
     */
    fun connectToPort(manager: android.hardware.usb.UsbManager, driver: UsbSerialDriver, selectedPortIndex: Int?) {
        // 1. Clean up previous state (Crucial for robust concurrency)
        disconnect()

        if (selectedPortIndex == null || selectedPortIndex < 0) {
            _errorChannel.tryEmit("Error: No valid port selected.")
            return
        }

        try {
            val connection = manager.openDevice(driver.device) ?: run {
                _errorChannel.tryEmit("Could not open device connection.")
                return
            }

            mPort = driver.ports[selectedPortIndex] 
            if (mPort == null) {
                _errorChannel.tryEmit("Failed to select port index $selectedPortIndex.")
                return
            }

            mPort?.open(connection)
            currentSelectedPortIndex = selectedPortIndex // Update state safely

            // Use app context here to ensure the settings object initialization is non-leaky.
            val prefs = AppPreferences(context) 
            val speed = if (prefs.useDefaultSpeed) 115200 else 921600
            mPort?.setParameters(speed, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Signal activation
            mPort?.dtr = true
            mPort?.rts = true

            _dataChannel.tryEmit("Successfully connected to port $selectedPortIndex.") // Use data channel for success message
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Connection failure during setup", e)
            _errorChannel.tryEmit("Failed to establish connection: ${e.message}")
        }
    }

    /**
     * Starts listening for incoming data stream. Must be called AFTER successful connectToPort().
     */
    fun startListening() {
        if (mPort == null) return

        val serialInputOutputListener = object : SerialInputOutputManager.Listener {
            override fun onRunError(errorMsg: Exception) {
                android.util.Log.w(TAG, "Run error encountered", errorMsg)
                _errorChannel.tryEmit("Runtime Error during streaming: ${errorMsg.message}")
            }

            override fun onNewData(data: ByteArray) {
                val hexString = data.toHex() + "\n"
                // Emit the received data packet asynchronously to listeners
                // Flow handles dispatching this back to the calling coroutine scope/thread model.
                CoroutineScope(Dispatchers.IO).launch { 
                    _dataChannel.emit(hexString)
                }
            }
        }

        serialInputOutputManager = SerialInputOutputManager(mPort!!, serialInputOutputListener)
        serialInputOutputManager!!.readTimeout = 0
        // Note: Starting this background task assumes proper lifecycle management in the caller.
        serialInputOutputManager!!.start()
    }

    /**
     * Sends command data.
     */
    fun sendCommand(command: ByteArray) {
        if (mPort == null) {
            _errorChannel.tryEmit("Cannot send command: No active port connection.")
            return
        }
        try {
            // Blocking call, but encapsulated here.
            mPort?.write(command, 0)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error sending command", e)
            _errorChannel.tryEmit("Error sending command: ${e.message}")
        }
    }

    /**
     * Safely closes all resources and resets state.
     */
    fun disconnect() {
        if (mPort != null || serialInputOutputManager != null) {
            // Important: Call close/stop even if the connection failed previously
            try {
                mPort?.close() 
            } catch (_: Exception) {}

            serialInputOutputManager?.stop()
            mPort = null
            serialInputOutputManager = null
            _dataChannel.tryEmit("Disconnected from USB CDC.") // Use data channel for status updates
        }
    }
}
```

Ключевая идея, которая залолжена в предложенный код состоит в использовании `MutableSharedFlow<String>` вместо callback-функций для отправки уведомлений в "MainActivity.kt". Каналы передачи данных определены как:

```java
// Use Flows/Channels for reactive streaming of events instead of callbacks.
companion object {
	const val TAG = "UsbService"
	private val _dataChannel = MutableSharedFlow<String>(extraBufferCapacity = 10)
	val dataChannel: SharedFlow<String> = _dataChannel

	private val _errorChannel = MutableSharedFlow<String>()
	val errorChannel: SharedFlow<String> = _errorChannel
}
```

Отправка сообщений об ошибках выглядит как:

```java
_errorChannel.tryEmit("Failed to select port index $selectedPortIndex.")
```

Информация об успешных действиях:

```java
_dataChannel.tryEmit("Successfully connected to port $selectedPortIndex.") // Use data channel for success message
```

Стоит заметить, что т.к. все сообщения всё равно попадают в одно окно, то можно было бы использовать один канал для доставки данных, а не два. С другой стороны, в оригинальном коде callback-функций было четыре и отличались они тем, что два сообщения кроме записи в журнал, ещё формировали Toast-ы. Вопрос дискуссионный.

В целом, вся идея модификации класса UsbConnectionManager строиться вокруг идея использования Flows/Channels для организации реактивных потоков вместо callback-функций, которые кажутся устаревшими.

Предложение по модификации "MainActivity.kt":

```kotlin
package ru.dors.androidusbcdc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button // Assuming standard Android widgets used for simplicity
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import kotlinx.coroutines.*
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : AppCompatActivity(), UsbConnectionManager.ConnectionListener {

    // 1. Use ViewModel (or simple lazy initialization) instead of instance property.
    // Here we use a simplified approach to manage the service reference lifecycle.
    private lateinit var usbManager: UsbConnectionManager
    
    // Coroutine scope tied to the activity's lifecycle for observing data streams
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val bindingViewModel: ViewModelProvider.() -> ViewModel = { 
        // In a real app, this would be a proper ViewModel/Repository layer injection
        object : ViewModel() {
            fun getUsbManager(): UsbConnectionManager = UsbConnectionManager(getApplicationContext())
        }
    }
    private val viewModel: ViewModel by viewModels { bindingViewModel() }

    // UI references (Using View Binding pattern mentally, but sticking to findViewById for minimal file changes)
    private lateinit var connectionMessageTextView: TextView
    private lateinit var listView: ListView 
    private lateinit var prefs: AppPreferences
    
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    // State management variables
    private var selectedPortIndex: Int = 0
    private val INTENT_ACTION_GRANT_USB = "UsbCdcApp.GRANT_USB"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // In production, replace this with ViewBinding implementation (e.g., binding.root)
        setContentView(R.layout.activity_main) 

        // Initialization of UI elements
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.app_toolbar)
        setSupportActionBar(toolbar)
        connectionMessageTextView = findViewById(R.id.connection_msg)
        listView = findViewById(R.id.listView)

        // Initialize Dependencies (Dependency Injection improvement)
        usbManager = viewModel.getUsbManager() 
        prefs = AppPreferences(this) // Preferences remain simple state readors

        // Setup UI components
        connectionMessageTextView.typeface = Typeface.MONOSPACE
        adapter = CdcPortsAdapter(this, arrayList)
        listView.adapter = adapter

        // Set up Listeners (Using lambda/click listeners is cleaner than anonymous objects)
        findViewById<Button>(R.id.button)?.setOnClickListener { 
            handleInitialDiscoveryAndConnect() 
        }
        findViewById<Button>(R.id.buttonExchange)?.setOnClickListener { 
            handleConnectionAttempt() 
        }
        findViewById<Button>(R.id.buttonClear)?.setOnClickListener {
            connectionMessageTextView.text = ""
        }

        // Subscribe to USB Data Streams (Reactivity)
        observeUsbDataStreams()
    }

    override fun onStart() {
        super.onStart()
        // Register receiver lifecycle remains sound for system broadcasts
        val intentFilter = IntentFilter(INTENT_ACTION_GRANT_USB)
        ContextCompat.registerReceiver(
            this, usbCdcStateReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        // Unregister receiver
        unregisterReceiver(usbCdcStateReceiver)
        // CRITICAL: Clean up connection resources when activity is stopped/destroyed
        usbManager.disconnect() 
    }
    
    /**
     * Observes the reactive data channels from UsbConnectionManager using coroutines.
     * This replaces all manual callback methods (onDataReceived, onError, etc.).
     */
    private fun observeUsbDataStreams() {
        // Launch collector in the ActivityScope, tying it to the ViewModel/Activity lifecycle 
        // (or Dispatchers.Main for simplicity here).
        activityScope.launch(Dispatchers.Main) {
            // Collect all incoming data streams until the job is cancelled (e.g., onStop/onDestroy)
            combine(
                UsbConnectionManager.dataChannel, 
                UsbConnectionManager.errorChannel
            ) { data, error -> Pair(data, error) }
                .collect { (data, _) ->
                    if (data != null) {
                        appendMessage("[DATA] $data")
                    }
                }

            // Handle errors separately for clearer distinction in UI logging
            UsbConnectionManager.errorChannel.onEach { message ->
                appendMessage("\n[ERROR] $message")
            }.launchIn(this) // Launching another collector scope
        }
    }


    /** Helper function to update the TextView on the main thread safely. */
    private fun appendMessage(message: String) {
         runOnUiThread { 
             connectionMessageTextView.append("\n$message")
         }
    }

    // Lifecycle Callbacks (Refactored callbacks to use the reactive stream logic, eliminating redundant calls)
    // These methods are now theoretically redundant if the entire system uses Flows, but kept for structure demonstration.
    /* 
    override fun onDataReceived(hexString: String) { ... } // REMOVE
    override fun onError(message: String) { ... }       // REMOVE
    // etc.
    */


    private fun handleInitialDiscoveryAndConnect() {
        // Logic remains mostly the same, focused only on USB discovery list building.
        val manager = getSystemService (Context.USB_SERVICE) as? UsbManager ?: return

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            connectionMessageTextView.text = getString(R.string.text_driver_unavailable)
            return
        }

        // ... [rest of discovery logic] ... 
        val driver = availableDrivers[0]
        findViewById<TextView>(R.id.textViewIdentification).text = driver.device.deviceName
        findViewById<TextView>(R.id.textViewDevice).text = "pid = ${driver.device.productId}, vid =  ${driver.device.vendorId}"

        val connection = manager.openDevice(driver.device) ?: run {
            appendMessage("\n[ERROR] USB Permission required.")
            // Re-request logic...
            return
        }

        arrayList.clear()
        val ports = usbManager.getAvailablePorts(manager, driver) // Use the refactored method
        arrayList.addAll(ports)
        adapter?.notifyDataSetChanged()
    }


    fun onItemClickListener(which: Int) {
        selectedPortIndex = which
        // Clear previous messages on selection change
        connectionMessageTextView.text = ""
        Toast.makeText(this.applicationContext, "Selected Port $which", Toast.LENGTH_SHORT).show()
    }

    private fun handleConnectionAttempt() {
        val manager = getSystemService (Context.USB_SERVICE) as? UsbManager ?: return
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) return

        val driver = availableDrivers[0]

        // 1. Attempt connection using the robust manager method
        usbManager.connectToPort(manager, driver, selectedPortIndex)

        if (selectedPortIndex >= 0) {
            try {
                // 2. If connected, start streaming and send command
                usbManager.startListening() // Start consuming data stream
                
                val request = if (prefs.useDSlipProtocol) {
                    byteArrayOf(0xB4.toByte(), 0x00, 0x81.toByte(), 0x00, 0x74)
                } else {
                    byteArrayOf(0x02, 0x03, 0x06, 0x37, 0xFE.toByte(), 0xC7.toByte())
                }
                usbManager.sendCommand(request)

            } catch (e: Exception) {
                appendMessage("\n[EXCEPTION] Failed during connection setup: ${e.message}")
            }
        } else {
            connectionMessageTextView.text = getString(R.string.please_select_port_first)
        }
    }

    // Broadcast receiver remains largely the same, but receives messages via dedicated helper function.
    private val usbCdcStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (INTENT_ACTION_GRANT_USB == intent.action) {
                val usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                // Use the centralized message helper function
                if (usbPermission) {
                    Toast.makeText(this@MainActivity, "Granted", Toast.LENGTH_LONG).show()
                    connectionMessageTextView.append("\n[STATUS] USB Permission granted.")
                } else {
                    Toast.makeText(this@MainActivity, "Denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-setting the listener is necessary if using ListView (old pattern)
        findViewById<ListView>(R.id.listView)?.onItemClickListener = 
            object : AdapterView.OnItemClickListener {
                override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long?) {
                    onItemClickListener(position)
                }
            }
    }
}
```

Также предложено переписать "CdcPortsAdapter.kt" с целью замены ListView на androidx.recyclerview.viewholder.RecyclerView, что потребует также внесения изменений в файле "activity_main.xml". Предложение по модификации:

```kotlin
package ru.dors.androidusbcdc

import android.content.Context
// We simulate migration to RecyclerView.Adapter pattern here.
import androidx.recyclerview.widget.RecyclerView 

/**
 * ARCHITECTURAL NOTE: This adapter should be refactored to extend RecyclerView.Adapter<*> 
 * and utilize View Binding instead of BaseAdapter, which is deprecated.
 */
class CdcPortsAdapter(context: Context) : 
    RecyclerView.Adapter<CdcPortsAdapter.PortViewHolder>() { // Assume this structure

    private var portList: List<CdcPortData> = emptyList()

    fun setPortList(list: List<CdcPortData>) {
        this.portList = list
        // Crucial step for performance: notify data change correctly
        notifyDataSetChanged() 
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortViewHolder {
        // Use View Binding here instead of findViewById(R.id.*)
        val binding = /* inflate with ViewBinding */ parent.context.createView(/* layout resource */)
        return PortViewHolder(binding)
    }

    override fun getItemCount(): Int = portList.size

    override fun onBindViewHolder(holder: PortViewHolder, position: Int) {
        val portData = portList[position]
        // Binding data via the ViewHolder wrapper class (improving separation)
        holder.bind(portData) 
    }


    /** Inner class representing a view holder */
    inner class PortViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Use generated binding accessors here in production code.
        val idNumber: TextView = view.findViewById(R.id.idNumber) 
        val writeEndpoint: TextView = view.findViewById(R.id.writeEndpoint)
        val readEndpoint: TextView = view.findViewById(R.id.readEndpoint)

        fun bind(data: CdcPortData) {
            // Clean, data-focused binding logic
            idNumber.text = data.id.toString()
            writeEndpoint.text = "Write Endpoint: ${data.writeEndpoint}"
            readEndpoint.text = "Read Endpoint: ${data.readEndpoint}"
        }
    }
}
```
