package ru.dors.androidusbcdc

import android.content.Context
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager

/**
 * Класс управляет установлением соединения и передачей данных
 * через последовательный порт USB CDC.
 * Этот класс отвечает только за низко-уровневое взаимодействие с "железом"
 */
class UsbConnectionManager(private val context: Context) {

    // Определяем Callback-интерфейс для информирования пользовательского
    // интерфейса (MainActivity - UI layer) об ошибках и полученных данных
    interface ConnectionListener {
        fun onDataReceived(hexString: String)
        fun onError(message: String)
        fun onConnectionSuccess(message: String)
        fun onConnectionFailure(message: String)
    }

    // Переменные, определяющее внутреннее состояние класса
    private var mPort: UsbSerialPort? = null
    var serialInputOutputManager: SerialInputOutputManager? = null

    // Экземпляр подписчика (внешний код) на события USB CDC
    private var listener: ConnectionListener? = null

    // Текущий индекс подключенного порта
    var currentSelectedPortIndex: Int = 0

    /**
     * Метод, через который можно указать подписчика на события
     */
    fun setConnectionListener(listener: ConnectionListener) {
        this.listener = listener
    }

    /**
     * Метод получает список достуных портов, основываясь на данных, полученных от UsbManager.
     * @param manager UsbManager позволяет начать работу с портами системы
     * @param driver драйвер для низко-уровневого взаимодействия с USB CDC
     * @return Список объектов CdcPortData для отображения в пользовательском интерфейсе (Endpoints)
     */
    fun getAvailablePorts(manager: android.hardware.usb.UsbManager, driver: UsbSerialDriver): List<CdcPortData> {
        val portList = mutableListOf<CdcPortData>()
        for (port in driver.ports) {
            try {

                val connection = manager.openDevice(driver.device) ?: continue
                port.open(connection)

                var writeEndpointAddr = 0
                if (port.writeEndpoint != null) {
                    writeEndpointAddr = port.writeEndpoint.address
                }

                var readEndpointAddr = 0
                if (port.readEndpoint != null) {
                    readEndpointAddr = port.readEndpoint.address
                }
                portList.add(CdcPortData(port.portNumber, writeEndpointAddr, readEndpointAddr))
            } catch (_: Exception) {
                // Handle ports that fail to open
                portList.add(CdcPortData(0, 0, 0))
            }
        }
        return portList
    }

    /**
     * Метод устанавливает соединения, используя указаный порт. Два порта могут быть определены
     * у Raspberry Pi Pico (REPL и data interface)
     * @param manager UsbManager позволяет начать работу с портами системы
     * @param driver драйвер для низко-уровневого взаимодействия с USB CDC
     */
    fun connectToPort(manager: android.hardware.usb.UsbManager, driver: UsbSerialDriver, selectedPortIndex: Int?) {

        // 1. Прекращаем предыдущее соединение, освобождаем занятые ресурсы
        disconnect()

        if (selectedPortIndex == null) {
            listener?.onError("Error: No port selected.")
            return
        }

        try {
            val connection = manager.openDevice(driver.device) ?: run {
                listener?.onConnectionFailure("Could not open device connection.")
                return
            }

            // 2. Устанавливаем порт, через который будет осуществляться дальнейшая работа
            mPort = driver.ports[selectedPortIndex]
            if (mPort == null) {
                listener?.onConnectionFailure("Failed to select port index $currentSelectedPortIndex.")
                return
            }

            mPort?.open(connection)

            // 3. Настраиваем параметры подключения: скорость обмена, и т.д.
            val prefs = AppPreferences(context) // Assuming this object is available for settings reading
            val speed = if (prefs.useDefaultSpeed) 115200 else 921600
            mPort?.setParameters(speed, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Сигнал готовности терминала (Readiness signal): Pico и Android начинают обмен данными
            mPort?.dtr = true

            // Request To Send signal — возведение это сигнала необходимо для начала
            // обмена данными между Arduino/Pico и Android
            mPort?.rts = true

            listener?.onConnectionSuccess("Successfully connected to port ${selectedPortIndex}.")

        } catch (e: Exception) {
            listener?.onError("Failed to establish connection: " + e.message)
        }
    }

    // ************ ОБРАБОТКА ДАННЫХ И УПРАВЛЕНИЕ ЖИЗНЕННЫМ ЦИКЛОМ ************

    /**
     * Метод осуществляет инициализацию подписчиков для обработки входящих данных,
     * поступающих по последовательному порту. Метод должен вызываться после connectToPort()
     */
    fun startListening() {
        if (mPort == null) return

        val serialInputOutputListener = object : SerialInputOutputManager.Listener {
            override fun onRunError(errorMsg: Exception) {
                // Уведомляем UI о возникновении ошибки. При использовании Pico, эта ошибка
                // возникает после переключения на REPL и возврат на data interface
                listener?.onError("Runtime Error: ${errorMsg.message}")
            }

            override fun onNewData(data: ByteArray) {
                // Передаём dump полученных данных для визуального анализа пользователем
                val hexString = data.toHex() + "\n"
                listener?.onDataReceived(hexString)
            }
        }

        // Начинаем обмен данными между Android приложением и микроконтроллером
        serialInputOutputManager = SerialInputOutputManager(mPort!!, serialInputOutputListener)
        serialInputOutputManager!!.readTimeout = 0
        serialInputOutputManager!!.start()
    }

    /**
     * Метод посылает конкретную команду (byte array) подключенному микроконтроллеру
     */
    fun sendCommand(command: ByteArray) {
        if (mPort == null) {
            listener?.onError("Cannot send command: No active port connection.")
            return
        }
        try {
            mPort?.write(command, 0)
        } catch (e: Exception) {
            listener?.onError("Error sending command: ${e.message}")
        }
    }

    /**
     * Метод закрывает соединение по последовательному порту и сбрасывает
     * внутренее состояние
     */
    fun disconnect() {
        // ВНИМАНИЕ! Убедитесь, что этот метод вызвается для освобождения ресурсов!
        mPort?.close()
        serialInputOutputManager?.stop()
        mPort = null
        serialInputOutputManager = null
        listener?.onConnectionSuccess("Disconnected from USB CDC.")
    }
}
