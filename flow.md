# Потоковая обработка данных посредством Flow

Flow — это механизм потоковой обработки данных в Kotlin, разработанный JetBrains и построенный на основе _coroutines_. Он предназначен для асинхронной работы с последовательностями значений.

Ключевые особенности Flow:

- Последовательная обработка. Flow работает в рамках одной последовательности, обрабатывая значения по мере их появления
- Интеграция с _coroutines_. Flow использует _coroutines_ для асинхронного выполнения, что делает его удобным для работы в Android
- Холодный поток (cold stream). Значения генерируются только при подписке (collect) и не сохраняются в памяти до момента запроса (подписки)
- Реактивность. Подходит для отслеживания изменений данных в реальном времени (например, обновлений из базы данных)

Flow не является реализацией шаблона publish/subscribe. Но  с помощью Flow можно построить систему, которая будет работать по логике publish/subscribe. Flow — инструмент, который хорошо подходит для реализации этого паттерна.

**SharedFlow** — служит "издателем" (publisher): через метод emit() в него можно отправлять события.

Определение Flow может выглядеть следующим образом:

```java
class UsbConnectionManager(private val context: Context) {

    companion object {

        private val _incomingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()
```

С помощью метода **emit**() передаются значения:

```java
override fun onNewData(data: ByteArray) {
    _incomingData.tryEmit(data)
}
```

Для получения данных применяется функция **collect**(), которая выполняется в контексте корутины:

```java
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
            incomingData.collect { data : ByteArray ->
                withContext(Dispatchers.Main) {
                    val textView = findViewById<TextView>(R.id.connection_msg)
                    textView.append("\n" + data.toHex())
                }
            }
        }
```


Flow позволяет осуществлять трансформацию данных, поддерживает операторы вроде map(), filter(), flatMapConcat() для изменения и фильтрации значений.

Flow позволяет управлять контекстом, т.е. можно задавать, в каком потоке выполнять операции (например, с помощью flowOn()).
