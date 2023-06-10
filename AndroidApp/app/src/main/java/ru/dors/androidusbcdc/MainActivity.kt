package ru.dors.androidusbcdc

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    var serialInputOutputManager: SerialInputOutputManager? = null

    // Параметры, необходимые для создания списка выбора порта
    private lateinit var listView: ListView
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    // Номер порта, который был выбран пользователем
    private var selectedPort: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

        val buttonExchange = findViewById<Button>(R.id.buttonExchange)
        buttonExchange.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {

                val manager = getSystemService (Context.USB_SERVICE)
                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(
                    manager as UsbManager?
                )
                if (availableDrivers.isEmpty()) return

                val driver = availableDrivers[0]

                // Подключаемся к устройству
                val port = driver.ports[selectedPort]

                // Open a connection to the first available driver.
                val connection = manager.openDevice(driver.device) ?: return

                val message = findViewById<TextView>(R.id.connection_msg)

                try {

                    port.open(connection)

                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

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
                                textView.text = data.toHex()
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
                    val request = ubyteArrayOf(0x02U, 0x03U, 0x06U, 0x37U, 0xFEU, 0xC7U).toByteArray()
                    port.write(request, 0)
                } catch (ignored: java.lang.Exception) {
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