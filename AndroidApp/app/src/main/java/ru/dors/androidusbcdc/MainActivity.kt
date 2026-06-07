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

    var serialInputOutputManager: SerialInputOutputManager? = null

    // Определяем идентификационную строку, которая используется при запросе
    // прав доступа к устройству
    private val INTENT_ACTION_GRANT_USB = "UsbCdcApp.GRANT_USB"

    // Параметры, необходимые для создания списка выбора порта
    private lateinit var listView: ListView
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    // Номер порта, который был выбран пользователем
    private var selectedPort: Int = 0

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
                selectedPort = i
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
                val connection = manager.openDevice(driver.device) ?: return

                // Вызов метод close() должен завершить поток, который слушает последовательный порт
                // в данный момент времени
                mPort?.close()

                // Получаем новый порт
                mPort = driver.ports[selectedPort]

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
                }

                val serialInputOutputListener: SerialInputOutputManager.Listener =
                    object : SerialInputOutputManager.Listener {
                        override fun onRunError(ignored: Exception) {}
                        override fun onNewData(data: ByteArray) {
                            runOnUiThread {
                                val textView = findViewById<TextView>(R.id.connection_msg)
                                textView.append(data.toHex() + "\n")
                            }
                        }
                    }

                serialInputOutputManager =
                    SerialInputOutputManager(mPort, serialInputOutputListener)
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

                        val portString = selectedPort.toString()
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
                serialInputOutputManager?.stop()

                // TODO: когда закрывать порт?
                //port.close();
            }
        })
    }
}
