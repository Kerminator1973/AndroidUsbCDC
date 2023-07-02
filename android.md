# Особенности разработки приложений для Android

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
