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

Пункт **app:showAsAction** позволяет указать, каким образом следует отобразить элемент меню. Если мы указываем значение **never**, то элемент указывается в виде иконки, а если **ifRoom**, то в виде текста, если есть достаточное количество места в AppBar.

Текст единственного пункта рекомендуется добавить в файл strings.xml:

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
