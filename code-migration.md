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
