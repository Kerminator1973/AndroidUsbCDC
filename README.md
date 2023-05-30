# Доступ к USB CDC в Android

Задача: подключить USB-устройство к Android-телефону по OTG.

Стартовая статья: https://github.com/Kerminator1973/RPIDev/blob/main/androidOTG.md

Начальное приложение было сгенерировано посредством Android Studio 2022.2.1. В качестве базового языка программирования был выбран Kotlin, как более синтаксически привлекательный, выразительный и компактный. Google рекомендует использовать его, а не Java.

Первое, что следует попробовать - подключить к репозитарию библиотеку от [Mik3y](https://github.com/mik3y/usb-serial-for-android) и попробовать определить, есть ли подключенное USB-устройство, или нет.

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

Ещё раз выполнил операцию "Sync now" и убедился, что сборка проекта прошла успешно.

В инструкции на сайте mik3y указано, что если приложение хочет получать уведомления о том, что USB-устройство было подключено, то следует добавить файл "device_filter.xml" в ваш проекта, в папку "res/xml/" и настроить "AndroidManifest.xml":

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

Однако, я пока не озаботился подобным функционалом.

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

Попытка подключения Raspberry Pi Pico завершилась на стадии поиска подключенных драйверов - Xiaomi Readmi 5A этот микроконтроллер не нашёл. 

**Update**: ошибка была связана с тем, что я использовал "чистую Pico", которую до этого сбросил до заводского состояния. После установки Bootloader-а CircuitPython, приложение дошло до фазы предоставления Permissions.

Исходя из истории изменений библиотеки, поддержка pid/vid для Raspberry Pi Pico была добавлена в Master-branch (v3.4.5) год назад:

``` xml
<usb-device vendor-id="11914" product-id="5"   /> <!-- 0x2E8A / 0x0005: Raspberry Pi Pico Micropython -->
<usb-device vendor-id="11914" product-id="10"  /> <!-- 0x2E8A / 0x000A: Raspberry Pi Pico SDK -->
```

Однако, эти параметры указаны в файле "/res/xml/device_filter.xml", которого нет в моём приложении.

Заметим, что в случае установки Bootloader-а AdaFruit, vendor-id = 0x239a, а product-id = 0x80f4:

``` console
developer@atmcheck:~$ lsusb
Bus 002 Device 004: ID 239a:80f4 Adafruit Pico
```

Работоспособность OTG-кабеля проверил подключением к телефону USB-флешки.

Важно заметить, что для обеспечения работоспособности решения, следует подключить OTG в телефон, а обычный microUSB-кабель в Raspberry PI Pico. Для получения информации о подключенном устройстве в приложение был добавлен следующий код:

``` kotlin
val message = "pid = ${driver.device.productId}, vid =  ${driver.device.vendorId}, Name = ${driver.device.deviceName}"
```

И при подключении Pico было получено сообщение: pid = 33012, vid = 9114, Name = `/dev/bus/usb/001/002!`. 33012 = 0x80F4, 9114 = 0x239A. Мы определённо увидели, что Raspberry Pi Pico подключен к телефону. Следующая задача, которую нужно решить - запросить у пользователя разрешение взаимодействовать с подключенным микроконтроллером.

## Что ещё можно почитать об этой библиотеке

Можно ещё почитать статью: https://forbot.pl/forum/topic/19927-komunikacja-raspberry-pi-pico-z-aplikacja-na-androida-poprzez-przewod-usb-cjava/

Статья японского разработчика, который решал подобную задачу: https://qiita.com/hiro-han/items/78b226b35174106259cd

Следует заметить, что оба разработчика (и польского, и японского) использовали "device_filter.xml". Например, в варианте японца это было:

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

Получить pid и vid прибора можно следующим образом:

``` java
usb_driver_.getDevice().getVendorId() // Pico : 11914
usb_driver_.getDevice().getProductId() // Pico : 10
```
