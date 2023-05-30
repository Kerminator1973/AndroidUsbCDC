package ru.dors.androidusbcdc

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button = findViewById<Button>(R.id.button)
        button.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {

                val message = findViewById<TextView>(R.id.connection_msg)

                val manager = getSystemService (Context.USB_SERVICE)
                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(
                    manager as UsbManager?
                )
                if (availableDrivers.isEmpty()) {
                    message.text = "No driver available"
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

                val port = driver.ports[0] // Most devices have just one port (port 0)
                message.text = "It's OK"
            }
        })
    }
}