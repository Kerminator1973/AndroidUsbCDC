# Особенности разработки приложений для Android

## Android Debug Bridge (adb)

Android - операционная система семейства Linux.

Для запуска консольных команд может быть использована утилита **Android Debug Bridge**, которая входит в состав Android Studio.

Используя компиляторы, входящие в состав Adroid Studio **Native Development** Kit (NDK) мы можем скомпилировать исходные текстов на ассемблере (arm-linux-androideabi-as), Си, или C++. Затем можно сгенерировать исполняемый файл, используя линковщик (arm-linux-androideabi-ld). Например:

``` shell
arm-linux-androideabi-as -al=hello_arm.lst -o hello_arm.o hello_arm.s
arm-linux-androideabi-ld  -o hello_arm hello_arm.o
```

Для информации - файлы с исходными текстами на ассемблере имеют расширение "s".

Используя adb мы можем загрузить исполняемый файл на телефон и запустить его:

``` shell
adb push hello_arm /data/local/tmp/hello_arm
adb shell chmod +x /data/local/tmp/hello_arm
adb shell /data/local/tmp/hello_arm
```

## Toast

Типовой способ использования Toast в Kotlin выглядит так:

``` kt
Toast.makeText(this.applicationContext, i.toString(), Toast.LENGTH_LONG).show()
```

## Как добавить AppBar

Официальная статья находится [здесь](https://developer.android.com/develop/ui/views/components/appbar/setting-up).

Для того, чтобы добавить в пользовательский интерфейс AppBar (Toolbar) достаточно включить его в разметку "activity_main.xml", как обычный дочерний элемент **ConstraintLayout**:

``` xml
<androidx.appcompat.widget.Toolbar
    android:id="@+id/app_toolbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    android:background="?attr/colorPrimary"
    android:elevation="4dp"
    android:theme="@style/ThemeOverlay.AppCompat.ActionBar"
    app:popupTheme="@style/ThemeOverlay.AppCompat.Light"/>
```

Следует заметить, что в случае использования ConstraintLayout, необходимо связать органы управления не с верхней границей главного окна, а с Toolbar, по его идентификатору **app_toolbar**.

Кроме этого, следует добавить его инициализацию при создании Activity (см. вызов setSupportActionBar()):

``` kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    setSupportActionBar(findViewById(R.id.app_toolbar))
```

Приведённых выше действий достаточно для того, чтобы AppBar появился, отобразил имя приложения и с ним можно было работать. Однако, для того, чтобы реализация была полноценной, следует разработать отдельный Layout как для вертикального, так и для горизонтального расположения экрана устройства.

## Как добавить меню в AppBar

Ключевая статья находится [здесь](https://developer.android.com/develop/ui/views/components/appbar/actions).

Сначала необходимо создать XML-описание меню в ресурсах приложения. Для этого следует выбрать пункт "Add Resource Directory" в проект и указать тип ресурса - "menu". Затем добавить в появившуюся папку описание, посредством команды "Menu Resource File".

Далее необходимо разработать содержимое меню, например:

``` xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <!-- Settings, should always be in the overflow -->
    <item android:id="@+id/action_settings"
        android:title="@string/action_settings"
        app:showAsAction="never"/>
</menu>
```

Пункт **app:showAsAction** позволяет указать, каким образом следует отобразить элемент меню. Если мы указываем значение **ifRoom**, то элемент будет отображён только тогда, когда для него достаточно места в AppBar. Если установить значение **never**, то текст элемента будет отображаться только в том случае, когда пользователь нажмёт на иконку вызова меню - "три вертикально размещённые точки".

Названием единственного пункта меню `android:title="@string/action_settings"` рекомендуется добавить в файл strings.xml:

``` xml
<string name="action_settings">Options</string>
```

Далее следует добавить реализацию метода onCreateOptionsMenu(), в которой в меню встраивается ресурс с описанием меню из файла "options_menu.xml" (который мы добавили ранее):

``` kt
override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    // Inflate the menu; this adds items to the action bar if it is present.
    menuInflater.inflate(R.menu.options_menu, menu)
    return true
}
```

Следует заметить, что при вставке текста, среда может добавить не корректные директивы импорта, например: Android.R, что может привести к сбоям при сборке проекта.

Обработчик выбора пункта меню может быть реализован посредством перегружаемого метода onOptionsItemSelected(). Например:

``` kt
override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
    R.id.action_settings -> {
        // Пользователь выбран пункт меню "Settings", поэтому нам следует перейти
        // в соответствующий Activity
        Toast.makeText(this.applicationContext, "Settings", Toast.LENGTH_LONG).show()
        true
    }

    else -> {
        // If we got here, the user's action was not recognized.
        // Invoke the superclass to handle it.
        super.onOptionsItemSelected(item)
    }
}
```

## Добавить новую Activity и перейти в неё

Android Studio содержит wizard, который позволяет сгенерировать шаблон новой Activity и включить его в проект.

Для перехода в эту Activity, следует создать Intent, т.н. намерение выполнить действие, указав его и вызвать метод startActivity():

``` kotlin
val intent = Intent(this, OptionsActivity::class.java)
intent.putExtra("key", value)
startActivity(intent)
```

В идеологии Android, форма пользовательского интерфейса является некоторой деятельностью/активностью (Activity). Часть активностей выполняет сама операционная система, а часть является уникальной для конкретного приложения.

Передавать параметры в Activity можно и вот так:

``` kt
startActivity(Intent(this, Page2::class.java).apply {
    putExtra("extra_1", value1)
    putExtra("extra_2", value2)
    putExtra("extra_3", value3)
})
```

### Добавить кнопку Back

Ключевая статья находится [здесь](https://www.geeksforgeeks.org/how-to-add-and-customize-back-button-of-action-bar-in-android/)

Гипотетически, самый простой способ обтработать нажатие кнопки "Back" - добавить следующий обработчик:

``` kt
    override fun onBackPressed() {
        finish()
    }
```

Такой подход работает, но только если нажимается аппаратная кнопка "Back", либо если выполняется жест "смахивание справа".

Более честным решение является добавления Toolbar-а. Мы можем включить в разметку вспомогательной Activity следующий код:

``` kt
<androidx.appcompat.widget.Toolbar
    android:id="@+id/back_toolbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    android:background="?attr/colorPrimary"
    android:elevation="4dp"
    android:theme="@style/ThemeOverlay.AppCompat.ActionBar"
    app:popupTheme="@style/ThemeOverlay.AppCompat.Light"/>
```

Кроме этого, мы должны активировать во вспомогательном Activity кнопку Home/Back:

``` kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_options)

    // Активируем в Toolbare кнопку Home
    setSupportActionBar(findViewById(R.id.back_toolbar))
    val actionBar: ActionBar? = supportActionBar
    actionBar?.setDisplayHomeAsUpEnabled(true)
}
```

Если скомпилировать решение, то кнопка "Back/Home" появится, но её нажатие не будет приводить ни к какому результату. Для того, чтобы выполнялся возврат в основной, или любой другой Activity, следует добавить описание родительского Activity в файле "AndroidManifest.xml". Например:

``` xml
<activity
    android:name=".OptionsActivity"
    android:parentActivityName=".MainActivity"
    android:exported="false" />
```

Свойство **parentActivityName** указывает, что родительским Activity является ".MainActivity" и при нажатии кнопки Back/Home управление будет передано в ".MainActivity".

## Добавить RadioGroup и RadioButton

Для того, чтобы иметь возможность управлять Radio-кнопками, их необходимо объединить в один контейнер - RadioGroup. Сделать это можно следующим образом:

``` xml
<RadioGroup
    android:id="@+id/protocolGroup"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginStart="16dp"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toBottomOf="@+id/protocolTextView">

    <RadioButton
        android:id="@+id/dslip_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="DSlip"
        tools:layout_editor_absoluteX="35dp"
        tools:layout_editor_absoluteY="86dp" />

    <RadioButton
        android:id="@+id/ccnet_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="CCNet"
        tools:layout_editor_absoluteX="39dp"
        tools:layout_editor_absoluteY="146dp" />

</RadioGroup>
```

Выбрать конкретную RadioButton можно используя _setter_:

``` kt
val dslipButton = findViewById<RadioButton>(R.id.dslip_button)
dslipButton?.isChecked = true
```

Обработать выбор пользователем кнопки можно используя обработчик **setOnCheckedChangeListener**:

``` kt
val protocolGroup = findViewById<RadioGroup>(R.id.protocolGroup)
protocolGroup.setOnCheckedChangeListener { _, checkedId ->
    findViewById<RadioButton>(checkedId)?.apply {
        Toast.makeText(this@OptionsActivity, text, Toast.LENGTH_LONG).show()
    }
}
```

## Сохранить выбранное значение в постоянной памяти

Наиболее простой, но не самый эффективный, способ сохранить некоторое значение в постоянной памяти - использовать SharedPreferences. Ключевая статья находится [здесь](https://www.digitalocean.com/community/tutorials/android-sharedpreferences-kotlin).

Чтобы сохранить значение может быть использован следующий код:

``` kt
val prefs =  getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
val protocol = (checkedId == R.id.dslip_button)

val editor = prefs.edit()
editor.putBoolean(getString(R.string.protocol_type), protocol)
editor.apply()
```

Следует заметить, что метод **apply**() выполняет отложенную запись, в отличие от метода **commit**(), который выполняет запись сразу же.

Считать ранее записанные данные можно следующим образом:

``` kt
val prefs =  getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
prefs.getBoolean(getString(R.string.protocol_type),true)
```
