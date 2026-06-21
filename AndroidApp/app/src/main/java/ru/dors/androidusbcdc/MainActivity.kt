package ru.dors.androidusbcdc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.hardware.usb.UsbManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.dors.androidusbcdc.UsbConnectionManager.Companion.incomingData

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    // Для взаимодействия с микроконтроллером будет использовать экземпляр вспомогательного
    // класса UsbConnectionManager
    private var usbManager: UsbConnectionManager = UsbConnectionManager(this)

    // Определяем идентификационную строку, которая используется при запросе
    // прав доступа к устройству
    private val INTENT_ACTION_GRANT_USB = "UsbCdcApp.GRANT_USB"

    // Параметры, необходимые для создания списка выбора порта
    private lateinit var recyclerView: RecyclerView
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    // Номер порта, который был выбран пользователем (index). Raspberry Pi Pico предоставляет
    // два порта: REPL и data exchange
    private var selectedPortIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.app_toolbar))

        // Считываем актуальные параметры для работы с приложением
        prefs = AppPreferences(this)

        // Настраиваем элементы пользовательского интерфейса
        val dumpView = findViewById<TextView>(R.id.connection_msg)
        dumpView.typeface = Typeface.MONOSPACE

        adapter = CdcPortsAdapter(this, arrayList, { position ->
            // Обработчик выбора пользователем порта/endpoints для обмена данными
            // (функциональная лямбда-функция)
            selectedPortIndex = position

            Toast.makeText(
                this.applicationContext,
                "Selected Port $position",
                Toast.LENGTH_SHORT).show()
        })

        // Для корректной работы, необходимо установить LayoutManager (вертикальный список)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Устанавливаем обработчики callback-вызовов от класса низкоуровневого
        // взаимодействия с микроконтроллером по USB CDC
        observeConnectionManager()
        //usbManager.setConnectionListener(this)

        // Кнопка: начальное подключение к микроконтроллеру, в том числе, для получения pid/vid
        val initialConnectButton = findViewById<Button>(R.id.button)
        initialConnectButton.setOnClickListener {
            handleInitialDiscoveryAndConnect()
        }

        // Кнопка: установить соединение и послать микроконтроллеру команду
        val buttonExchange = findViewById<Button>(R.id.buttonExchange)
        buttonExchange.setOnClickListener {
            handleConnectionAttempt()
        }

        // Кнопка: очистить поле с результатами логирования
        val buttonClear = findViewById<Button>(R.id.buttonClear)
        buttonClear.setOnClickListener {
            findViewById<TextView>(R.id.connection_msg).text = ""
        }
    }

    private fun observeConnectionManager() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    incomingData.collect { data : ByteArray ->
                        withContext(Dispatchers.Main) {
                            val textView = findViewById<TextView>(R.id.connection_msg)
                            textView.append(data.toHex() + "\n")
                        }
                    }
                }
                launch {
                    UsbConnectionManager.errors.collect { message : String ->
                        withContext(Dispatchers.Main) {
                            val textView = findViewById<TextView>(R.id.connection_msg)
                            textView.append("\n[ERROR] $message")
                        }
                    }
                }
                launch {
                    // Наблюдение за успешным подключением
                    UsbConnectionManager.connection_success.collect { message : String ->
                        withContext(Dispatchers.Main) {
                            val textView = findViewById<TextView>(R.id.connection_msg)
                            textView.append("\n[STATUS] $message\n")
                            Toast.makeText(this@MainActivity,
                                message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    // Наблюдение за разрывом соединения
                    UsbConnectionManager.connection_failure.collect { message : String ->
                        withContext(Dispatchers.Main) {
                            val textView = findViewById<TextView>(R.id.connection_msg)
                            textView.append("\n[FAILURE] $message")
                            Toast.makeText(this@MainActivity,
                                "Connection Failed",Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    // --- Методы управления жизненным циклом ---

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Включаем описание меню из ресурса "options_menu" в качестве меню в AppBar
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    // Обработчик нажатия кнопки "Options" в пользовательском интерфейсе
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            val intent = Intent(this, OptionsActivity::class.java)
            startActivity(intent)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // --- Вспомогательные функции ---

    private fun handleInitialDiscoveryAndConnect() {
        val manager = getSystemService (USB_SERVICE) as UsbManager?
        if (manager == null) return

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) {
            findViewById<TextView>(R.id.connection_msg).text = getString(R.string.text_driver_unavailable)
            return
        }

        // Используем первое доступное устройство для получения параметров подключения (pid/vid)
        val driver = availableDrivers[0]

        // Выводим информацию об имени устройства, а также о pid/vid
        findViewById<TextView>(R.id.textViewIdentification).text = driver.device.deviceName

        val textViewDevice = findViewById<TextView>(R.id.textViewDevice)
        "pid = ${driver.device.productId}, vid =  ${driver.device.vendorId}".also { textViewDevice.text = it }

        // Open a connection to the first available driver.
        val connection = manager.openDevice(driver.device)
        if (connection == null) {

            // Permissions будут отсутствовать, если отказаться запустить приложение
            // при подключении кабеля к мобильному телефону

            val textView = findViewById<TextView>(R.id.connection_msg)
            val message = getString(R.string.text_need_permission)
            textView.append("\n[ERROR] $message")

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

        // Формируем список доступных Endpoints для взаимодействия с микроконтроллером,
        // а затем передаём этот список адаптеру RecyclerView
        arrayList.clear()
        val ports = usbManager.getAvailablePorts(manager, driver)
        arrayList.addAll(ports)
        adapter?.notifyDataSetChanged()
    }

    /**
     * Метод подключается к микроконтроллеру и передаём ему конкретную команду
     */
    private fun handleConnectionAttempt() {

        val manager = getSystemService (USB_SERVICE) as? UsbManager? ?: return
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        if (availableDrivers.isEmpty()) return

        val driver = availableDrivers[0]

        usbManager.connectToPort(manager, driver, selectedPortIndex)
        if (selectedPortIndex >= 0) {
            try {
                // Если подключение было успешным, подписываемся на сообщения микроконтроллера
                usbManager.startListening()

                // Конкретная команда зависит от используемого протокола
                val request = if (prefs.useDSlipProtocol) {
                    byteArrayOf(0xB4.toByte(), 0x00, 0x81.toByte(), 0x00, 0x74)
                } else {
                    byteArrayOf(0x02, 0x03, 0x06, 0x37, 0xFE.toByte(), 0xC7.toByte())
                }
                usbManager.sendCommand(request)

            } catch (_: Exception) {

                val textView = findViewById<TextView>(R.id.connection_msg)
                val message = getString(R.string.text_exception)
                textView.append("\n[EXCEPTION] $message")
            }
        } else {
            findViewById<TextView>(R.id.connection_msg).text = getString(R.string.please_select_port_first)
        }
    }

    // Метод подписывается на системые сообщения (Broadcast Receiver). Цель - если система
    // разрешит работу с USB CDC, метод информирует об этом пользователя
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
}
