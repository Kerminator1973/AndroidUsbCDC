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
import android.widget.AdapterView.OnItemClickListener
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialProber

// Removed unused imports that deal with internal communication logic (e.g., UsbSerialPort, SerialInputOutputManager)

class MainActivity : AppCompatActivity(), UsbConnectionManager.ConnectionListener {

    private lateinit var prefs: AppPreferences

    // Use the manager instance to handle all serial communications
    private var usbManager: UsbConnectionManager = UsbConnectionManager(this)

    // Определяем идентификационную строку, которая используется при запросе
    // прав доступа к устройству
    private val INTENT_ACTION_GRANT_USB = "UsbCdcApp.GRANT_USB"

    // Параметры, необходимые для создания списка выбора порта
    private lateinit var listView: ListView
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    // Номер порта, который был выбран пользователем (index)
    private var selectedPortIndex: Int = 0 // Renamed variable for clarity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.app_toolbar))

        // Считываем актуальные параметры для работы с приложением
        prefs = AppPreferences(this)

        // Setup UI elements (same as before)
        val dumpView = findViewById<TextView>(R.id.connection_msg)
        dumpView.typeface = Typeface.MONOSPACE

        adapter = CdcPortsAdapter(this, arrayList)
        listView = findViewById(R.id.listView)
        listView.adapter = adapter

        // === Setup Listeners for the Manager (The central point of communication) ===
        usbManager.setConnectionListener(this)

        // --- Button Click Listener 1: Initial Connection Attempt ---
        val initialConnectButton = findViewById<Button>(R.id.button) // Assuming this button starts discovery/connection
        initialConnectButton.setOnClickListener {
            handleInitialDiscoveryAndConnect()
        }

        // --- Button Click Listener 2: Exchange (Reconnect using selected port) ---
        val buttonExchange = findViewById<Button>(R.id.buttonExchange)
        buttonExchange.setOnClickListener {
            handleConnectionAttempt()
        }

        // --- Button Clear ---
        val buttonClear = findViewById<Button>(R.id.buttonClear)
        buttonClear.setOnClickListener {
            findViewById<TextView>(R.id.connection_msg).text = ""
        }
    }

    // --- Lifecycle Management (Kept the same) ---

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(INTENT_ACTION_GRANT_USB)
        ContextCompat.registerReceiver(
            this,
            usbCdcStateReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(usbCdcStateReceiver)
    }

    // --- Callback Handling (Implemented ConnectionListener interface) ---

    /**
     * Called by UsbConnectionManager when data is received from the USB CDC device.
     */
    override fun onDataReceived(hexString: String) {
        runOnUiThread {
            val textView = findViewById<TextView>(R.id.connection_msg)
            textView.append(hexString)
        }
    }

    /**
     * Called by UsbConnectionManager when an error occurs during connection/data transfer.
     */
    override fun onError(message: String) {
        runOnUiThread {
            val textView = findViewById<TextView>(R.id.connection_msg)
            textView.append("\n[ERROR] $message")
        }
        Toast.makeText(this, "Connection Error", Toast.LENGTH_SHORT).show()
    }

    /**
     * Called by UsbConnectionManager when the connection is successfully established.
     */
    override fun onConnectionSuccess(message: String) {
        runOnUiThread {
            val textView = findViewById<TextView>(R.id.connection_msg)
            textView.append("\n[STATUS] $message")
            Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Called by UsbConnectionManager when a connection attempt fails.
     */
    override fun onConnectionFailure(message: String) {
        runOnUiThread {
            val textView = findViewById<TextView>(R.id.connection_msg)
            textView.append("\n[FAILURE] $message")
        }
        Toast.makeText(this, "Connection Failed", Toast.LENGTH_SHORT).show()
    }

    // --- UI Listeners (Simplified logic using the manager) ---

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            val intent = Intent(this, OptionsActivity::class.java)
            startActivity(intent)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // --- Private Helper Functions for Action Delegation ---

    private fun handleInitialDiscoveryAndConnect() {
        val manager = getSystemService (USB_SERVICE) as android.hardware.usb.UsbManager?
        if (manager == null) return

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            findViewById<TextView>(R.id.connection_msg).text = getString(R.string.text_driver_unavailable)
            return
        }

        val driver = availableDrivers[0] // Use the first available device for initial check

        // 1. Display Device Info (UI logic remains here)
        findViewById<TextView>(R.id.textViewDevice).text = "pid = ${driver.device.productId}, vid =  ${driver.device.vendorId}"
        findViewById<TextView>(R.id.textViewIdentification).text = driver.device.deviceName

        // 2. Populate the port list using the manager helper
        arrayList.clear()
        val ports = usbManager.getAvailablePorts(manager, driver)
        arrayList.addAll(ports)
        adapter?.notifyDataSetChanged()

        // 3. Initiate connection attempt (which will use the selected port index from the UI interaction later)
    }

    fun onItemClickListener(view: View?, which: Int) {
        // This listener handles setting 'selectedPortIndex' based on ListView selection
        selectedPortIndex = which
        Toast.makeText(this.applicationContext, "Selected Port $which", Toast.LENGTH_SHORT).show()
    }

    /**
     * Handles the action of initiating connection using the currently selected port.
     */
    private fun handleConnectionAttempt() {
        val manager = getSystemService (USB_SERVICE) as? android.hardware.usb.UsbManager? ?: return
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) return

        val driver = availableDrivers[0] // Assume connection attempt uses the same device found initially

        // Delegate Connection Setup to the Manager
        usbManager.connectToPort(manager, driver, selectedPortIndex)

        // After successful setup, start listening for data and sending initial command
        if (selectedPortIndex >= 0) {
            try {
                // Start monitoring incoming serial data
                usbManager.startListening()

                // Send the mandatory identification command
                val request = if (prefs.useDSlipProtocol) {
                    byteArrayOf(0xB4.toByte(), 0x00, 0x81.toByte(), 0x00, 0x74)
                } else {
                    byteArrayOf(0x02, 0x03, 0x06, 0x37, 0xFE.toByte(), 0xC7.toByte())
                }
                usbManager.sendCommand(request)

            } catch (e: Exception) {
                // Handled by onError callback if communication fails immediately
            }
        } else {
            findViewById<TextView>(R.id.connection_msg).text = "Please select a port first."
        }
    }

    // --- Broadcast Receiver (Kept the same) ---
    private val usbCdcStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (INTENT_ACTION_GRANT_USB == intent.action) {
                val usbPermission = intent.getBooleanExtra(
                    UsbManager.EXTRA_PERMISSION_GRANTED,
                    false
                )

                if (usbPermission) {
                    Toast.makeText(this@MainActivity, "Granted", Toast.LENGTH_LONG).show()
                    findViewById<TextView>(R.id.connection_msg).text = getString(R.string.try_one_more_time)
                } else {
                    Toast.makeText(this@MainActivity, "Denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Initialization of the Adapter (Need to override or call setup separately since we removed onCreate's bulk logic)
    override fun onResume() {
        super.onResume()
        // Re-initialize UI listeners after activity resumes
        findViewById<ListView>(R.id.listView).onItemClickListener =
            OnItemClickListener { _, _, i, _ ->
                onItemClickListener(null, i) // Call the helper function
            }
    }

}

/*
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
import android.widget.AdapterView.OnItemClickListener
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    // Добавлена зависимость от класса, обеспечивающего доступ к настройкам приложения
    private lateinit var prefs: AppPreferences

    // Use the manager instance to handle all serial communications
    private var usbManager: UsbConnectionManager = UsbConnectionManager(this)

    //var serialInputOutputManager: SerialInputOutputManager? = null

    // Определяем идентификационную строку, которая используется при запросе
    // прав доступа к устройству
    private val INTENT_ACTION_GRANT_USB = "UsbCdcApp.GRANT_USB"

    // Параметры, необходимые для создания списка выбора порта
    private lateinit var listView: ListView
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    // Номер порта, который был выбран пользователем
    private var selectedPortIndex: Int = 0

    // Объект, посредством которого осуществляется взаимодействие по USB CDC
    private var mPort : UsbSerialPort? = null

    // Методы onStart() и onStop() используются для организации подписки и отказа
    // от подписки на события, связанные с получением права работы с устройством USB CDC
    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(INTENT_ACTION_GRANT_USB)
        ContextCompat.registerReceiver(
            this,
            usbCdcStateReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(usbCdcStateReceiver)
    }

    // Обработчик широковещательного сообщения о получении/отказе права работать с USB CDC
    private val usbCdcStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (INTENT_ACTION_GRANT_USB == intent.action) {

                // Информация о том, удалось ли получить доступ, или нет, хранится
                // в дополнительном параметре с именем UsbManager.EXTRA_PERMISSION_GRANTED (строка)
                val usbPermission = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )

                if (usbPermission) {
                    Toast.makeText(this@MainActivity, "Granted", Toast.LENGTH_LONG).show()

                    val message = findViewById<TextView>(R.id.connection_msg)
                    message.text = getString(R.string.try_one_more_time)
                }
                else
                {
                    Toast.makeText(this@MainActivity, "Denied", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Включаем описание меню из ресурса "options_menu" в качестве меню в AppBar
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            // Пользователь выбран пункт меню "Settings", поэтому нам следует перейти
            // в соответствующий Activity
            val intent = Intent(this, OptionsActivity::class.java)
            startActivity(intent)
            true
        }

        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.app_toolbar))

        // Считываем актуальные параметры для работы с приложением
        prefs = AppPreferences(this)

        // Изменяем шрифт, которым выводится ответ подключенного прибора. По умолчанию,
        // Android не использует моноширинный шрифт, из-за чего полученные данные не выравнены
        val dumpView = findViewById<TextView>(R.id.connection_msg)
        dumpView.typeface = Typeface.MONOSPACE

        // Осуществляем подготовительные действия для работы с COM-портом
        adapter = CdcPortsAdapter(this, arrayList)

        listView = findViewById(R.id.listView)
        listView.adapter = adapter

        // Взаимодействие с микроконтроллером будет осуществляться при нажатии экранной кнопки
        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {

                val manager = getSystemService (USB_SERVICE)
                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(
                    manager as UsbManager?
                )
                if (availableDrivers.isEmpty()) return

                val driver = availableDrivers[0]

                val message = findViewById<TextView>(R.id.connection_msg)
                if (availableDrivers.isEmpty()) {
                    message.text = getString(R.string.text_driver_unavailable)
                    return
                }

                // Выводим информацию о подключенном устройстве
                val textViewDevice = findViewById<TextView>(R.id.textViewDevice)
                "pid = ${driver.device.productId}, vid =  ${driver.device.vendorId}".also { textViewDevice.text = it }

                val textViewIdentification = findViewById<TextView>(R.id.textViewIdentification)
                textViewIdentification.text = driver.device.deviceName

                // Open a connection to the first available driver.
                val connection = manager.openDevice(driver.device)
                if (connection == null) {

                    // Possibly, need permissions
                    message.text = getString(R.string.text_need_permission)

                    // permissions будут отсутствовать, если отказаться запустить приложение
                    // при подключении кабеля к мобильному телефону

                    // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
                    val usbPermissionIntent = PendingIntent.getBroadcast(
                        this@MainActivity,
                        0,
                        Intent(INTENT_ACTION_GRANT_USB).apply {
                            setPackage(packageName) // делает Intent явным — привязывает к этому приложению
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    manager.requestPermission(driver.device, usbPermissionIntent)
                    return
                }

                // Добавляем все доступные порты в общий список
                arrayList.clear()

                for(port in driver.ports) {

                    try {
                        port.open(connection)

                        var writeEndpointAddr = 0
                        if (port.writeEndpoint != null)
                            writeEndpointAddr = port.writeEndpoint.address

                        var readEndpointAddr = 0
                        if (port.readEndpoint != null)
                            readEndpointAddr = port.readEndpoint.address

                        arrayList.add(CdcPortData(port.portNumber, writeEndpointAddr, readEndpointAddr))

                    } catch (_: Exception) {
                        arrayList.add(CdcPortData(0, 0,0))
                    }
                }

                // При закрытии любого порта, выполняется и закрытие connection. По этой причине,
                // если прибор предоставляет несколько port-ов (например, Raspberry Pi Pico предоставляет
                // два порта: REPL и CDC), то закрытие любого порта приведёт к тому, что информация по
                // остальным портам не будет получена
                if (driver.ports.isNotEmpty()) {
                    driver.ports[0].close()
                }

                // Уведомляем адаптер ListView об изменении списка доступных портов
                adapter!!.notifyDataSetChanged()
            }
        })

        listView.onItemClickListener =
            OnItemClickListener { _, _, i, _ ->
                // Запоминаем выборанный номер порта
                selectedPortIndex = i
                Toast.makeText(this.applicationContext, i.toString(), Toast.LENGTH_LONG).show()
            }

        val buttonClear = findViewById<Button>(R.id.buttonClear)
        buttonClear.setOnClickListener {
            val textView = findViewById<TextView>(R.id.connection_msg)
            textView.text = ""
        }

        val buttonExchange = findViewById<Button>(R.id.buttonExchange)
        buttonExchange.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {

                val manager = getSystemService (USB_SERVICE)
                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(
                    manager as UsbManager?
                )
                if (availableDrivers.isEmpty()) return

                // Подключаемся к устройству
                val driver = availableDrivers[0]

                // Вызов метода close() должен завершить поток, который слушает последовательный порт
                // в данный момент времени
                mPort?.close()

                val connection = manager.openDevice(driver.device) ?: return

                // Получаем новый порт
                mPort = driver.ports[selectedPortIndex]

                val message = findViewById<TextView>(R.id.connection_msg)

                try {

                    mPort?.open(connection)

                    // Устанавливаем скорость взаимодействия с прибором в зависимости от настройки
                    val speed = if (prefs.useDefaultSpeed) 115200 else 921600
                    mPort?.setParameters(speed, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                    // Сигнал готовности терминала: Pico и Android начинают обмен данными
                    mPort?.dtr = true
                    // Request To Send signal — возведение это сигнала необходимо для начала
                    // обмена данными между Arduino/Pico и Android
                    mPort?.rts = true
                } catch (_: Exception) {
                    message.text = getString(R.string.text_exception)
                    return
                }

                val serialInputOutputListener: SerialInputOutputManager.Listener =
                    object : SerialInputOutputManager.Listener {

                        override fun onRunError(errorMsg: Exception) {
                            runOnUiThread {
                                val textView = findViewById<TextView>(R.id.connection_msg)
                                textView.append(errorMsg.message + "\n")
                            }
                        }

                        override fun onNewData(data: ByteArray) {
                            runOnUiThread {
                                val textView = findViewById<TextView>(R.id.connection_msg)
                                textView.append(data.toHex() + "\n")
                            }
                        }
                    }

                serialInputOutputManager =
                    SerialInputOutputManager(mPort, serialInputOutputListener)

                // Осуществляется блокирующая операция чтения ответа микроконтроллера
                serialInputOutputManager!!.readTimeout = 0

                // Обработка сообщений от микроконтроллера будет осуществляться в отдельном потоке
                //val rx = Executors.newSingleThreadExecutor()
                //rx.submit(serialInputOutputManager)

                serialInputOutputManager!!.start()

                // Запускаем отдельный поток, который будет отправлять в устройство одну и
                // ту же команду каждый 500 мс
                //var co100Ms = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
                try {

                    val request = if (prefs.useDSlipProtocol) {
                        // DSlip: identification
                        byteArrayOf(0xB4.toByte(), 0x00, 0x81.toByte(), 0x00, 0x74)
                    } else {
                        // CCNet: identification
                        byteArrayOf(0x02, 0x03, 0x06, 0x37, 0xFE.toByte(), 0xC7.toByte())
                    }

                    mPort?.write(request, 0)

                    runOnUiThread {
                        val textView = findViewById<TextView>(R.id.connection_msg)

                        val portString = selectedPortIndex.toString()
                        val finalMessage = getString(
                            R.string.connection_message_template,
                            portString
                        )

                        textView.text = finalMessage
                    }

                } catch (_: java.lang.Exception) {
                    runOnUiThread {
                        val textView = findViewById<TextView>(R.id.connection_msg)
                        textView.append("Exception during write command\n")
                    }
                }
                //}, 0, 500, TimeUnit.MILLISECONDS)

                // Мы отправили сообщение и serialInputOutputManager должен выйти из scope.
                // Вероятно, его нужно остановить здесь
                //serialInputOutputManager?.stop()

                // TODO: когда закрывать порт?
                //port.close();
            }
        })
    }
}
*/