package ru.dors.androidusbcdc

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {

    var serialInputOutputManager: SerialInputOutputManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
                    "No driver available".also { message.text = it }
                    return
                }

                // Open a connection to the first available driver.
                val driver = availableDrivers[0]

                val connection = manager.openDevice(driver.device)
                if (connection == null) {

                    // Possibly, need permissions
                    val text = "Can't open device. pid = ${driver.device.productId}, vid =  ${driver.device.vendorId}, Name = ${driver.device.deviceName}"
                    message.text = text

                    // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
                    return
                }

                // Подключаемся к устройству
                val port = driver.ports[1] // Most devices have just one port (port 0)

                try {
                    port.open(connection)

                    port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

                    // Сигнал готовности терминала: Pico и Android начинают обмен данными
                    port.dtr = true
                    // Request To Send signal — возведение это сигнала необходимо для начала
                    // обмена данными между Arduino/Pico и Android
                    port.rts = true
                } catch (exception: Exception) {

                    "Exception...".also { message.text = it }
                }

                val serialInputOutputListener: SerialInputOutputManager.Listener =
                    object : SerialInputOutputManager.Listener {
                        override fun onRunError(ignored: Exception) {}
                        override fun onNewData(data: ByteArray) {
                            runOnUiThread {
                                val textView = findViewById<TextView>(R.id.connection_msg)
                                textView.append(data.toHex())
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
        })
    }

    // При уничтожении Activity, отписываемся от подписки на события и останавливаем
    // код, обрабатывающий поступающие от микроконтроллера данные
    override fun onDestroy() {
        //serialInputOutputManager!!.stop()
        //unregisterReceiver(broadcastReceiver)
        super.onDestroy()
    }

    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}