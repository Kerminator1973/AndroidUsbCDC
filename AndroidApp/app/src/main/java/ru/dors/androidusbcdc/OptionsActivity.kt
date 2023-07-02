package ru.dors.androidusbcdc

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        // Активируем в Toolbar кнопку Home
        setSupportActionBar(findViewById(R.id.back_toolbar))
        val actionBar: ActionBar? = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        // Устанавливаем значения Radio-кнопок по умолчанию
        val dslipButton = findViewById<RadioButton>(R.id.dslip_button)
        dslipButton?.isChecked = true

        val defaultSpeedButton = findViewById<RadioButton>(R.id.default_speed_button)
        defaultSpeedButton?.isChecked = true

        // Добавляем обработчики выбора элементов группы
        val protocolGroup = findViewById<RadioGroup>(R.id.protocolGroup)
        protocolGroup.setOnCheckedChangeListener { _, checkedId ->
            findViewById<RadioButton>(checkedId)?.apply {
                Toast.makeText(this@OptionsActivity, text, Toast.LENGTH_LONG).show()
            }
        }

        val speedGroup = findViewById<RadioGroup>(R.id.speedGroup)
        speedGroup.setOnCheckedChangeListener { _, checkedId ->
            findViewById<RadioButton>(checkedId)?.apply {
                Toast.makeText(this@OptionsActivity, text, Toast.LENGTH_LONG).show()
            }
        }
    }
}