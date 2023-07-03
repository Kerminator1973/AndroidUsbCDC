package ru.dors.androidusbcdc

import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
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
        val prefs =  getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
        if (prefs.getBoolean(getString(R.string.protocol_type),true)) {
            val dslipButton = findViewById<RadioButton>(R.id.dslip_button)
            dslipButton?.isChecked = true
        } else {
            val ccnetButton = findViewById<RadioButton>(R.id.ccnet_button)
            ccnetButton?.isChecked = true
        }

        if (prefs.getBoolean(getString(R.string.speed_value),true)) {
            val defaultSpeedButton = findViewById<RadioButton>(R.id.default_speed_button)
            defaultSpeedButton?.isChecked = true
        } else {
            val megabitSpeedButton = findViewById<RadioButton>(R.id.megabit_speed_button)
            megabitSpeedButton?.isChecked = true
        }

        // Добавляем обработчики выбора элементов группы
        val protocolGroup = findViewById<RadioGroup>(R.id.protocolGroup)
        protocolGroup.setOnCheckedChangeListener { _, checkedId ->
            findViewById<RadioButton>(checkedId)?.apply {

                val prefs =  getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
                val protocol = (checkedId == R.id.dslip_button)

                val editor = prefs.edit()
                editor.putBoolean(getString(R.string.protocol_type), protocol)
                editor.apply()
            }
        }

        val speedGroup = findViewById<RadioGroup>(R.id.speedGroup)
        speedGroup.setOnCheckedChangeListener { _, checkedId ->
            findViewById<RadioButton>(checkedId)?.apply {

                val prefs =  getSharedPreferences("USB_CDC_PREFS", Context.MODE_PRIVATE)
                val speed = (checkedId == R.id.default_speed_button)

                val editor = prefs.edit()
                editor.putBoolean(getString(R.string.speed_value), speed)
                editor.apply()
            }
        }
    }
}