package ru.dors.androidusbcdc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    var serialInputOutputManager: SerialInputOutputManager? = null

    // Параметры работы приложения
    var useDSlipProtocol = true
    var useDefaultSpeed = true

    // Определяем идентификационную строку, которая используется при запросе
    // прав доступа к устройству
    private val INTENT_ACTION_GRANT_USB = "UsbCdcApp.GRANT_USB"

    // Параметры, необходимые для создания списка выбора порта
    private lateinit var listView: ListView
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    // Номер порта, который был выбран пользователем
    private var selectedPort: Int = 0

    // Методы onStart() и onStop() используются для организации подписки и отказа
    // от подписки на события, связанные с получением права работы с устройством USB CDC
    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter(INTENT_ACTION_GRANT_USB)
        registerReceiver(usbCdcStateReceiver, intentFilter)
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
                var usbPermission = intent.getBooleanExtra(
                        UsbManager.EXTRA_PERMISSION_GRANTED,
                        false
                    )

                if (usbPermission) {
                    Toast.makeText(this@MainActivity, "Granted", Toast.LENGTH_LONG).show()

                    val message = findViewById<TextView>(R.id.connection_msg)
                    message.text = "Try one more time!"
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
        val prefs =  getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
        useDSlipProtocol = prefs.getBoolean(getString(R.string.protocol_type),true)
        useDefaultSpeed = prefs.getBoolean(getString(R.string.speed_value),true)

        // Осуществляем подготовительные действия для работы с COM-портом
        adapter = CdcPortsAdapter(this, arrayList)

        listView = findViewById(R.id.listView)
        listView.adapter = adapter

        // Взаимодействие с микроконтроллером будет осуществляться при нажатии экранной кнопки
        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {

                val message = findViewById<TextView>(R.id.connection_msg)

                val manager = getSystemService (Context.USB_SERVICE)

                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(
                    manager as UsbManager?
                )
                if (availableDrivers.isEmpty()) {
                    message.text = getString(R.string.text_driver_unavailable)
                    return
                }

                val driver = availableDrivers[0]

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
                    val flags =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_MUTABLE else 0
                    val usbPermissionIntent = PendingIntent.getBroadcast(
                        this@MainActivity,
                        0,
                        Intent(INTENT_ACTION_GRANT_USB),    // Это просто идентификационная строка
                        flags
                    )
                    manager.requestPermission(driver.device, usbPermissionIntent)
                    return
                }

                // Добавляем все доступные порты в общий список
                arrayList.clear()
                for(port in driver.ports) {

                    try {
                        port.open(connection)

                        var writeEnpointAddr = 0;
                        if (port.writeEndpoint != null)
                            writeEnpointAddr = port.writeEndpoint.address

                        var readEnpointAddr = 0;
                        if (port.readEndpoint != null)
                            readEnpointAddr = port.readEndpoint.address

                        arrayList.add(CdcPortData(port.portNumber, writeEnpointAddr, readEnpointAddr))

                        // TODO: порт нужно закрывать, т.к. потом не удасться подключиться ещё раз
                        // Нюанс состоит в том, что close() ещё и connection закрывает
                        //port.close();

                    } catch (exception: Exception) {
                        arrayList.add(CdcPortData(0, 0,0))
                    }
                }

                // Закрываем как порт, так и connection
                if (driver.ports.size > 0) {
                    driver.ports[0].close();
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
        };

        val buttonExchange = findViewById<Button>(R.id.buttonExchange)
        buttonExchange.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {

                val manager = getSystemService (Context.USB_SERVICE)

                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(
                    manager as UsbManager?
                )
                if (availableDrivers.isEmpty()) return

                // Подключаемся к устройству
                val driver = availableDrivers[0]
                val port = driver.ports[selectedPort]
                val connection = manager.openDevice(driver.device) ?: return
                val message = findViewById<TextView>(R.id.connection_msg)

                try {

                    port.open(connection)

                    // Устанавливаем скорость взаимодействия с прибором в зависимости от настройки
                    val speed = if (useDefaultSpeed) 115200 else 921600
                    port.setParameters(speed, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                    // Сигнал готовности терминала: Pico и Android начинают обмен данными
                    port.dtr = true
                    // Request To Send signal — возведение это сигнала необходимо для начала
                    // обмена данными между Arduino/Pico и Android
                    port.rts = true
                } catch (exception: Exception) {
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
                    SerialInputOutputManager(port, serialInputOutputListener)
                serialInputOutputManager!!.readTimeout = 0
                // Обработка сообщений от микроконтроллера будет осуществляться в отдельном потоке
                val rx = Executors.newSingleThreadExecutor()
                rx.submit(serialInputOutputManager)

                // Запускаем отдельный поток, который будет отправлять в устройство одну и
                // ту же команду каждый 500 мс
                //var co100Ms = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
                try {

                    // DSlip: identification
                    //val request = ubyteArrayOf(0xB4U, 0x00U, 0x81U, 0x00U, 0x74U).toByteArray()

                    // CCNet: identification
                    //val request = ubyteArrayOf(0x02U, 0x03U, 0x06U, 0x37U, 0xFEU, 0xC7U).toByteArray()

                    val request = if (useDSlipProtocol)
                        ubyteArrayOf(0xB4U, 0x00U, 0x81U, 0x00U, 0x74U).toByteArray()
                    else ubyteArrayOf(0x02U, 0x03U, 0x06U, 0x37U, 0xFEU, 0xC7U).toByteArray()

                    port.write(request, 0)

                    runOnUiThread {
                        val textView = findViewById<TextView>(R.id.connection_msg)
                        textView.text = "Written\n"
                    }

                } catch (ignored: java.lang.Exception) {
                    runOnUiThread {
                        val textView = findViewById<TextView>(R.id.connection_msg)
                        textView.append("Exception during write command\n")
                    }
                }
                //}, 0, 500, TimeUnit.MILLISECONDS)

                // TODO: когда закрывать порт?
                //port.close();
            }
        });
    }

    fun ByteArray.toHex(): String = joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }
}

//Class CdcPortsAdapter
class CdcPortsAdapter(private val context: Context, private val arrayList: java.util.ArrayList<CdcPortData>) : BaseAdapter() {
    private lateinit var idNumber: TextView
    private lateinit var writeEndpoint: TextView
    private lateinit var readEndpoint: TextView
    override fun getCount(): Int {
        return arrayList.size
    }
    override fun getItem(position: Int): Any {
        return position
    }
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {

        var convertView: View?
        convertView = LayoutInflater.from(context).inflate(R.layout.row, parent, false)
        idNumber = convertView.findViewById(R.id.idNumber)
        writeEndpoint = convertView.findViewById(R.id.writeEndpoint)
        readEndpoint = convertView.findViewById(R.id.readEndpoint)

        idNumber.text = arrayList[position].getId().toString()
        writeEndpoint.text = "Write Endpoint: " + arrayList[position].getWriteEndpoint()
        readEndpoint.text = "Read Endpoint: " + arrayList[position].getReadEndpoint()

        return convertView
    }
}