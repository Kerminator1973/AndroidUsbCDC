# Добавить ListView в Kotlin/Android

Стартовая статья [How to write a custom adapter for my list view on Android using Kotlin?](https://www.tutorialspoint.com/how-to-write-a-custom-adapter-for-my-list-view-on-android-using-kotlin) от Azhar.

Последовательность действий:

- Разрабатываем модель, т.к. класс, поля которого будут отбражатся в отдельном пункте ListView. В моём приложении этот класс называется CdcPortData
- Разработать разметку отдельного элемента ListView. В моем приложении файл находится в res/layout и называется "row.xml"
- Добавить в разметку Activity (файл "activity_main.xml") элемент ListView
- Разработать адаптер, который инициализируется списком ArrayList, в котором будут хранится экземпляры класса-модели (CdcPortData). Задача адаптера - связать модель и представление, которое берётся из файла "row.xml"
- Написать код, который будет заполнять ArrayList экземплярами класса-модели
- Связать адаптер и ListView с данными
- Связать ListView с адаптером

## Реализация модели

Реализация модели может выглядесь следующим образом:

``` kt
class CdcPortData(private var id: Int, private var writeEndpoint: Int,
                  private var readEndpoint: Int
) {
    fun getId(): Int {
        return id
    }

    fun getWriteEndpoint(): Int {
        return writeEndpoint
    }

    fun getReadEndpoint(): Int {
        return readEndpoint
    }
}
```

Kotlin позволяет определить и проинициализировать private-поля сразу в определении класса. См.: id, writeEndpoint и readEndpoint. В качестве публичных методов достаточно добавить getter-ы.

## Разметка отдельного элемента списка

Разметка отдельного элемента списка - это xml-файл, который хранится в папке "/res/layout". Реализация может выглядеть следующим образом:

``` xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/idNumber"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="8dp"
        android:text="Number"
        android:textColor="@android:color/holo_purple"
        android:textSize="20sp"
        android:textStyle="bold"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/writeEndpoint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginTop="8dp"
        android:text="WriteEndpoint"
        android:textColor="@color/black"
        android:textSize="16sp"
        app:layout_constraintStart_toEndOf="@+id/idNumber"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/readEndpoint"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:text="ReadEndpoint"
        android:textColor="@color/black"
        android:textSize="16sp"
        app:layout_constraintStart_toEndOf="@+id/idNumber"
        app:layout_constraintTop_toBottomOf="@+id/writeEndpoint" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

## Включить ListView в MainActivity

Для этого достаточно изменить файл "activity_main.xml" из папки "/res/layout". Добавить нужно, приблизительно следующий код:

``` xml
<ListView
    android:id="@+id/listView"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_marginStart="16dp"
    android:layout_marginTop="16dp"
    android:layout_marginEnd="16dp"
    android:layout_marginBottom="16dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toStartOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toBottomOf="@+id/connection_msg" />
```

## Разработать адаптер ArrayList to ListView

Задача адаптера - связать модель (ArrayList, в котором хранятся экземпляры класса CdcPortData) и представление, которое берётся из файла "row.xml"

Пример реализации:

``` kt
class CdcPortsAdapter(private val context: Context, private val arrayList: java.util.ArrayList<CdcPortData>) : BaseAdapter() {
    private lateinit var idNumber: TextView
    private lateinit var writeEndpoint: TextView
    private lateinit var readEndpoint: TextView

    override fun getCount(): Int {
        return arrayList.size
    }

    override fun getItem(position: Int): Any {
        return position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, reusedConvertView: View?, parent: ViewGroup): View? {
        val convertView : View? = reusedConvertView ?: LayoutInflater.from(context).inflate(R.layout.row, parent, false)
        if (null != convertView) {
            idNumber = convertView.findViewById(R.id.idNumber)
            idNumber.text = arrayList[position].getId().toString()

            writeEndpoint = convertView.findViewById(R.id.writeEndpoint)
            writeEndpoint.text = "Write Endpoint: " + arrayList[position].getWriteEndpoint()

            readEndpoint = convertView.findViewById(R.id.readEndpoint)
            readEndpoint.text = "Read Endpoint: " + arrayList[position].getReadEndpoint()
        }

        return convertView
    }
}
```

Класс является производным от **BaseAdapter**.

В этом определении, в классе определены и проинициализированы: context, который используется для доступа к ресурсам приложения и arrayList, в котором хранятся данные для отображения.

**ВАЖНО**: ListView пытается повторно использовать convertView. Первый раз он передаётся в getView()нулевым, конструируется и возвращается обратно в ListView. В случае необходимости сформировать ещё одну строки списка, повторно используется (recycled) ранее созданный convertView.

## Передача данных в Adapter

Список доступных портов - это член-класса, реализующего Activity:

``` kt
class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        arrayList.clear()
        for(port in driver.ports) {
            ...
            arrayList.add(CdcPortData(port.portNumber, writeEnpointAddr, readEnpointAddr))
        }
```

Если список в ListView динамически изменяется, его можно обновить используя следующий код:

``` kt
arrayList.clear()
arrayList.add(CdcPortData(0, 0,0))
adapter!!.notifyDataSetChanged()
```

## Связывание модели (данные), адаптера и ListView

Типовой код связывания может выглядеть следующим образом:

``` kt
class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private var arrayList: ArrayList<CdcPortData> = ArrayList()
    private var adapter: CdcPortsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = CdcPortsAdapter(this, arrayList)
        listView = findViewById(R.id.listView)
        listView.adapter = adapter
```

Мы создаём экземпляр класса-адаптера, инициализируея его контейнером, хранящим экземпляры класса CdcPortData. Важно заметить, что эта инициализация предполагает, что мы не сможет затем удалить адаптер из создать новый - это приведёт к потере связи и развале логики работы Activity. Вместо этого, мы должны использовать единожды созданный контейнер.

Далее, нам достаточно лишь проинициализировать ссылку на адаптер в экземпляре класса ListView.
