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
