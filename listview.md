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
