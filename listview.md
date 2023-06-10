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

Если список в ListView динамически изменяется, его можно обновить используя следующий код:

``` kt
arrayList.clear()
arrayList.add(CdcPortData(0, 0,0))
adapter!!.notifyDataSetChanged()
```

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

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {

        var convertView: View?
        convertView = LayoutInflater.from(context).inflate(R.layout.row, parent, false)
        idNumber = convertView.findViewById(R.id.idNumber)
        writeEndpoint = convertView.findViewById(R.id.writeEndpoint)
        readEndpoint = convertView.findViewById(R.id.readEndpoint)

        idNumber.text = arrayList[position].getId().toString()
        writeEndpoint.text = "Write Endpoint: " + arrayList[position].getWriteEndpoint()
        readEndpoint.text = "Read Endpoint: " + arrayList[position].getReadEndpoint()

        return convertView
    }
}
```

Класс является производным от **BaseAdapter**.

В этом определении, в классе определены и проинициализированы: context, который используется для доступа к ресурсам приложения и arrayList, в котором хранятся данные для отображения.


- Разработать адаптер, который инициализируется списком ArrayList, в котором будут хранится экземпляры класса-модели (CdcPortData). Задача адаптера - связать модель и представление, которое берётся из файла "row.xml"
- Написать код, который будет заполнять ArrayList экземплярами класса-модели
- Связать адаптер и ListView с данными
- Связать ListView с адаптером
