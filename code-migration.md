# Миграция кода на актуальные версии ПО в 2026 г.

Для того, чтобы обновить сборку в Gradle-проекте, можно ли зайти в "app\build.gradle", либо в раздел "Project Structures" -> "Dependencies" в Android Studio и заменить указанный номер сборки на желаемый. Было:

```gradle
dependencies {
    implementation 'com.github.mik3y:usb-serial-for-android:3.5.1'
```

Стало:

```gradle
dependencies {
    implementation 'com.github.mik3y:usb-serial-for-android:3.10.0'
```

При миграции библиотеки 'usb-serial-for-android' c версии 3.5.1 на 3.10.0, при сборке возникла следующая ошибка:

```
e: file:///D:/Sources/AndroidUsbCDC/AndroidApp/app/src/main/java/ru/dors/androidusbcdc/MainActivity.kt:285:27 Argument type mismatch: actual type is 'SerialInputOutputManager?', but 'Runnable!' was expected.
Fix with AI
```

```java
serialInputOutputManager =
    SerialInputOutputManager(mPort, serialInputOutputListener)
serialInputOutputManager!!.readTimeout = 0

// Обработка сообщений от микроконтроллера будет осуществляться в отдельном потоке
val rx = Executors.newSingleThreadExecutor()
rx.submit(serialInputOutputManager)
```

Я предположил, что ошибка связана с изменением API библиотеки и Claude Sonnet 4.6 это подтверждает: "_В версии 3.7.0 библиотеки usb-serial-for-android класс SerialInputOutputManager перестал реализовывать интерфейс Runnable. Именно поэтому Executors.submit(), ожидающий Runnable, больше не принимает его напрямую._"

Sonnet 4.6 рекомендует заменить две строки:

```java
val rx = Executors.newSingleThreadExecutor()
rx.submit(serialInputOutputManager)
```

На одну строку:

```java
serialInputOutputManager!!.start()
```

Ещё одна рекомендация: _"Также не забудьте, что там, где вы завершаете работу (например, в onPause или onDestroy), теперь нужно вызывать serialInputOutputManager?.stop() вместо любого ручного управления потоком через Executor."_

## Обновление конфигураций проекта (Gradle)

Android Studio обновлялась систематически и это, в том числе, приводило к обновлению Gradle. При обновлении Gradle запускался процесс обновления конфигурационных файлов. Как результат, на момент миграции проекта (май 2026 г.), используется Gradle 9.4.1.

## Обновление других библиотек

Обновление других библиотек осуществляется через Android Studio, раздел "Project Structure" -> "Dependencies". Обновлены:

- androidx.appcompat:appcompat с 1.4.1 до 1.7.1
- androidx.constraintlayout:constraintlayout c 2.1.3 до 2.2.1
- androidx.core:core-ktx c 1.8.0 до 1.13.0
- android.test.espresso:espresso-core с 3.5.1 до 3.7.0
- android.test.ext:junit с 1.1.5 до 1.3.0
- com.google.android.material:material с 1.5.0 до 1.14.0

После изменения версий компонентов вознили коллизии по версии SDK: _app is currently compiled against android-33. Update this project to use a newer compileSdk of at least 34, for example 37._

Переход на SDK 34 позволил решить значительную часть несовместимости, но всё равно осталось две зависимости, которые требуют SDK 36.

### Обеспечение совместимости

Проект был собран с использованием SDK 36 (Android 14), но приложение вполне успешно работает на Android 10.

Совместимость обеспечивается с помощью манифеста "AndroidManifest.xml", в котором описываются следующие параметры:

- minSdkVersion — минимальный уровень API, на котором приложение гарантированно работает. Если версия ОС ниже, установка будет заблокирована
- targetSdkVersion — уровень API, для которого приложение протестировано. Определяет, какие функции совместимости активирует система
- compileSdkVersion — версия SDK, используемая для компиляции. Позволяет использовать новейшие API на этапе разработки

Гипотетически, версии должны быть указаны приблизительно так:

```xml
<uses-sdk
    android:minSdkVersion="21"
    android:targetSdkVersion="34"
    android:compileSdkVersion="36" />
```

Для моего проекта установлен в "AndroidManifest.xml" только один параметр, который можно трактовать, как версия SDK:

```xml
<application
    tools:targetApi="31">
```

Но вот в Gradle все эти параметры указываются и очень похоже, что именно они и используются в системе:

```
android {
    namespace 'ru.dors.androidusbcdc'
    compileSdk 36

    defaultConfig {
        applicationId "ru.dors.androidusbcdc"
        minSdk 26
        targetSdk 33
        versionCode 1
        versionName "1.0"
```

А SDK 26 - это Android 8 Oreo. Это объясняет работоспособность приложения на телефоне с Android 10.

## Замечания к приложению

Первое предупреждение: _"usbCdcStateReceiver is missing RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag for unprotected broadcasts registered for UsbCdcApp.GRANT_USB"_

```java
override fun onStart() {
    super.onStart()
    val intentFilter = IntentFilter(INTENT_ACTION_GRANT_USB)
    registerReceiver(usbCdcStateReceiver, intentFilter)
}
```

**Причина предупреждения**:

Начиная с Android 12 (API 31), а строго обязательно с Android 13 (API 33), при динамической регистрации BroadcastReceiver необходимо явно указывать флаг видимости ресивера. Android хочет знать, должен ли ваш ресивер принимать broadcasts от других приложений или только от вашего.

Исправление замечания:

```java
import androidx.core.content.ContextCompat
```

```java
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
```

### Следующее замечание

Замечание к коду: _"Mutable implicit PendingIntent will throw an exception once this app starts targeting Android 14 or above, follow either of these recommendations: for an existing PendingIntent use FLAG_NO_CREATE and for a new PendingIntent either make it immutable or make the Intent within explicit"_, относится к коду:

```java
val usbPermissionIntent = PendingIntent.getBroadcast(
    this@MainActivity,
    0,
    Intent(INTENT_ACTION_GRANT_USB),    // Это просто идентификационная строка
    flags
)
```

**Суть проблемы**:

Предупреждение говорит о двух нарушениях одновременно:

- Mutable — flags не содержит FLAG_IMMUTABLE
- Implicit — Intent(INTENT_ACTION_GRANT_USB) создаёт неявный интент (только строка-действие, без компонента)

В Android 14+ такая комбинация бросает исключение.

Переработанный код:

```java
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
```

### Неиспользуемый параметр в описании исключения

В Kotlin можно заменить неиспользуемый параметр на символ `_`, что явно указывает на то, что результат операции не используется. Решение в моём коде:

```java
} catch (_: Exception) {
    arrayList.add(CdcPortData(0, 0,0))
}
```