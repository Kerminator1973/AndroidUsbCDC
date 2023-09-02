# Исследование поведения Android

К сожалению, большинство примеров не отвечают на исключительно важные вопросы, связанные с функционированием приложений Android, например:

- Если назначить кнопке две обработчика событий, то будут ли они работать одновременно?
- Будет ли обработчик нажатия кнопки работать дальше, если в нём будет выполнен код `return@setOnClickListener`?
- Как прекратить обработку некоторого события (нажатия на кнопку)?

Для того, чтобы понять, как ведёт себя система и как именно нужно писать код, имеет смысл разработать специализированное исследовательско-экспериментальное приложение.

Сгенерировать такое приложение можно средствами Android Studio, по шаблону "Empty Views Activity". В этом случае, будет сгенерирован код приложения в котором будет один Layout в разделе "res", но ничего лишнего не будет.

# Вывод отладочной информации

Вывод отладочной информации осществляется функцией Log.d(). Пример:

```kt
import android.util.Log
...
Log.d("Kermit", "First Listener is handling the click event...")
```

В случае вызова, TAG и строка появятся в закладке "Logcat".

## Будут ли работать два обработчика события?

Выполняется следующий код:

```kt
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val button = findViewById<Button>(R.id.button)
    button.setOnClickListener(object : View.OnClickListener {
        override fun onClick(v: View?) {
            Log.d("Kermit", "The first Listener is handling the click event...")
        }
    });

    button.setOnClickListener(object : View.OnClickListener {
        override fun onClick(v: View?) {
            Log.d("Kermit", "The second Listener is handling the click event...")
        }
    });
}
```

При нажатии на кнопку "Button" выполняется только один обработчик - тот, который был назначен последним. В Logcat мы увидим только одно сообщение: "_The second Listener is handling the click event..._". Это поведение объясняет, почему в множестве примеров обработчик события определён как лямбда-функция - отсутствует риск бесконечного дублирования обработчиков событий, при их повторном назначении.

Соответственно, для того, чтобы прекратить обработку события, достаточно написать следующий код:

```kt
button.setOnClickListener(null)
```

## Что просходит при вызове return@setOnClickListener в обработчике?

В экспериментальном примере добавлен следующий код:

```kt
button.setOnClickListener {
    Log.d("Kermit", "The listener is handling the click event...")
    return@setOnClickListener
    Log.d("Kermit", "This part of code is executing...")
}
```

В приведенном выше примере, вторая запись в лог не будет добавлена. Конструкция `return@setOnClickListener` работает как обычный **return**. Особенность кода состоит в том, что обычный return не может быть скомпилирован: _'return' is not allowed here_
