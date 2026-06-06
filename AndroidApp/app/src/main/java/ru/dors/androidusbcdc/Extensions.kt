package ru.dors.androidusbcdc

// Метод выводит байтовый массив в виде строки с шестнадцатеричными числами в блоках
// по двенадцать чисел. В общем случае, только 12 чисел помещается на экране телефона
// при использовании моноширинного шрифта

fun ByteArray.toHex(): String = buildString {
    val bytes = this@toHex
    for (i in 0 until bytes.size step 12) {
        val chunk = bytes.slice(i until minOf(bytes.size, i + 12))
        append(chunk.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) })
        append('\n')
    }
}
