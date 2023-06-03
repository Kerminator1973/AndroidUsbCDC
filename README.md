# Доступ к USB CDC в Android

Задача: подключить USB-устройство к Android-телефону по OTG.

Стартовая статья: https://github.com/Kerminator1973/RPIDev/blob/main/androidOTG.md

Начальное приложение было сгенерировано посредством Android Studio 2022.2.1. В качестве базового языка программирования был выбран Kotlin, как более синтаксически привлекательный, выразительный и компактный. Google рекомендует использовать его, а не Java.

Первое, что следует попробовать - подключить к репозитарию библиотеку от [Mik3y](https://github.com/mik3y/usb-serial-for-android) и определить, есть ли подключенное USB-устройство, или нет.

## Подключение библиотеки

В файл "settings.gradle", находящийся в корне репозитария добавил следующую строку:

```
dependencyResolutionManagement {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Сразу же выполнил операцию "Sync now" и убедился, что сборка проекта прошла успешно.

В файл сборки конкретного проекта "\app\build.gradle" добавил зависимость - включил библиотеку Mik3y:

```
dependencies {
    implementation 'com.github.mik3y:usb-serial-for-android:3.5.1'
}
```

Ещё раз выполнил операцию "Sync now" и убедился, что сборка проекта прошла успешно. ВНИМАНИЕ! Синхронизацию необходимо обязательно выполнить, т.к. если этого не сделать, то Android Studio не загрузит зависимость и проект не будет собираться.

## Разработка кода

Если удасться подключить библиотеку, то следующим этапом можно попробовать получить список драйверов для всех подключенных устройств (их может быть несколько, если используется USB Hub), а затем открыть соединение с первым из них:

``` java
// Find all available drivers from attached devices.
UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
if (availableDrivers.isEmpty()) {
	return;
}

// Open a connection to the first available driver.
UsbSerialDriver driver = availableDrivers.get(0);
UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
if (connection == null) {
	// add UsbManager.requestPermission(driver.getDevice(), ..) handling here
	return;
}

UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
port.open(connection);
port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
```

По факту, был добавлен вот такой код на Kotlin:

``` kotlin
import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialProber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService (Context.USB_SERVICE)
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(
                manager as UsbManager?
            )
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        val driver = availableDrivers[0]
        val connection = manager.openDevice(driver.device)
        if (connection == null) {
            // add UsbManager.requestPermission(driver.getDevice(), ..) handling here
            return;
        }

        val port = driver.ports[0] // Most devices have just one port (port 0)
```

## Обработка события о подключении устройства к телефону. Предоставление прав работы с ним

В инструкции на сайте mik3y указано, что если приложение хочет получать уведомления о том, что USB-устройство было подключено, то следует добавить файл [device_filter.xml](https://github.com/mik3y/usb-serial-for-android/blob/master/usbSerialExamples/src/main/res/xml/device_filter.xml) в проект, в папку "/src/main/res/xml/", а также добавить в файл "AndroidManifest.xml" ссылку на intent-filter подключения конкретного USB-устройства к мобильному телефону:

```
<activity
    android:name="..."
    ...>
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

В дополнение к содержимому файла "device_filter.xml" я добавил ещё одну строку с указанием pid/vid, который возвращает Raspberry Pi Pico, на который установлен bootloader CircuitPython:

``` xml
<usb-device vendor-id="9114" product-id="33012" /> <!-- 0x239a / 0x80f4 Adafruit Pico CircuitPython -->
```

Указанные pid/vid были получены при подключении Pico к персональному компьютеру, работающему под Ubunte Mate 22.04:

``` console
developer@atmcheck:~$ lsusb
Bus 002 Device 004: ID 239a:80f4 Adafruit Pico
```

После компиляции и установки приложения на мобильный телефон, при подключении микроконтроллера Pi Pico, на экране телефона появляется всплывающее окно с текстом "Запустить Android USB CDC при подключении этого устройства?". Если нажать кнопку "OK", то будет запущено наше приложение и ему будет предоставлено право обмениваться данными по USB, без дополнительной настройки прав.

### Физическое подключение Pico к телефону

Попытка подключения Raspberry Pi Pico завершилась обнаружением подключенного Pico. Тем не менее, два условия должны быть выполнены:

- микроконтроллер Pico должен активировать USB CDC. Что можно сделать установив Bootloader-а CircuitPython и добавив файл boot.py
- подключив OTG-кабель к телефону, а не к Pico

Содержимое файла "boot.py":

``` python
import usb_cdc
usb_cdc.enable(console=True, data=True)
```

Причина, по которой OTG-кабель следует подключать к телефону становится ясна после рассмотрения распиновки кабеля:

![image](./OTG-soldering.png)

ID соединён с GROUND и по этому признаку, телефон понимает, что он должен работать в режиме клиента, а не Host-а.

Работоспособность OTG-кабеля можно проверить подключив к телефону USB-флешку.

### Вспомогательные функции

Для получения информации о подключенном устройстве в приложение был добавлен следующий код:

``` kotlin
val message = "pid = ${driver.device.productId}, vid =  ${driver.device.vendorId}, Name = ${driver.device.deviceName}"
```

При подключении Pico было получено сообщение: pid = 33012, vid = 9114, Name = `/dev/bus/usb/001/002!`.

Выполнить преобразование массива байт в hex-строку можно с помощью следующей функции:

``` kt
fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
```

## Обмен данными с Raspberry Pico

В демонстрационном приложении, подключение к микроконтроллеру осуществляется при нажатии на экранную кнопку:

``` kotlin
val button = findViewById<Button>(R.id.button)
button.setOnClickListener(object : View.OnClickListener {
    override fun onClick(v: View?) {
        ...
    }
})
```

При физическом подключении Raspberry Pi Pico, телефон видит только один драйвер - `/dev/bus/usb/001/002`:

``` kt
if (availableDrivers.isEmpty()) {
	"No driver available".also { message.text = it }
	return
}

message.text = "Drivers: ${availableDrivers.size}\n"

for (item in availableDrivers) {
	message.append(item.device.deviceName)
	message.append("\n")
}
```

Но портов у этого драйвера два: нулевой порт используется для REPL (Write endpoint: 2, Read Endpoint: 130), а первый порт - USB CDC (Write endpoint: 4, Read Endpoint: 132). Логирование может быть выполнено таким образом:

``` kt
port.open(connection)

if (port.writeEndpoint != null) {
    message.append("  Write Endpoint: ${port.writeEndpoint.address}")
}

if (port.readEndpoint != null) {
    message.append("  Read Endpoint: ${port.readEndpoint.address}")
}

if (port.serial != null) {
    message.append("  Serial: ${port.serial}")
}
```

В качестве основы кода для обмена данными с Pico, был использован код из [статьи разработчика из Польши](https://forbot.pl/forum/topic/19927-komunikacja-raspberry-pi-pico-z-aplikacja-na-androida-poprzez-przewod-usb-cjava/). Особенности кода:

- используются DTR и RTS
- взаимодействие с микроконтроллером осуществляется из рабочего потока
- обрабатывается не только событие подключения устройства, но и его отключение

### Использование DTR и RTS

``` kt
port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

// Сигнал готовности терминала: Pico и Android начинают обмен данными
port.dtr = true
// Request To Send signal — возведение это сигнала необходимо для начала 
// обмена данными между Arduino/Pico и Android
port.rts = true
```

### Обработчик данных, полученных от микроконтроллера

В моём приложении, полученные данные от микроконтроллера добавляются к строке с текстом (textView):

``` kt
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
```

Запуск потока осуществляется следующим образом:

``` kt
serialInputOutputManager =
    SerialInputOutputManager(port, serialInputOutputListener)
serialInputOutputManager!!.readTimeout = 0
// Обработка сообщений от микроконтроллера будет осуществляться в отдельном потоке
val rx = Executors.newSingleThreadExecutor()
rx.submit(serialInputOutputManager)
```

### Отправка контроллеру команды

Отправить команду контроллеру можно следующим образом:

``` kt
try {
    val request = ubyteArrayOf(0x02U, 0x03U, 0x06U, 0x37U, 0xFEU, 0xC7U).toByteArray()
    port.write(request, 0)
} catch (ignored: java.lang.Exception) {
}
```

Если мы хотим выполнять команду систематически, например, для получения состояния какого-то объекта, мы можем использовать планировщик:

``` kt
var co100Ms = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
    try {
        val request = ubyteArrayOf(0x02U, 0x03U, 0x06U, 0x37U, 0xFEU, 0xC7U).toByteArray()
        port.write(request, 0)
    } catch (ignored: java.lang.Exception) {
    }
}, 0, 500, TimeUnit.MILLISECONDS)
```

Следует заметить, что при отключении устройства, планировщик должен быть остановлен, для чего используется co100Ms.

## Что ещё можно почитать об этой библиотеке

 в которой используется DTR и RTS. Код, приведённой в этой статье позволил считать какую-то информацию из порта, но, наиболее вероятно, что это был REPL.

Статья японского разработчика, который решал подобную задачу: https://qiita.com/hiro-han/items/78b226b35174106259cd

Следует заметить, что оба разработчика (и польского, и японского) использовали "device_filter.xml". Например, японский разработчик подключал только Raspberry Pi Pico:

``` xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <usb-device vendor-id="11914" product-id="10" />
</resources>
```

Этот файл связан с "AndroidManifest.xml":

``` xml
<uses-feature android:name="android.hardware.usb.host" />
<action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
<meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" android:resource="@xml/device_filter" />
```
