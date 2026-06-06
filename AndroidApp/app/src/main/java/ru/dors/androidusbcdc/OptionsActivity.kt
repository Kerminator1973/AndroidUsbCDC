package ru.dors.androidusbcdc

import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity

class OptionsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)

        setSupportActionBar(findViewById(R.id.back_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = AppPreferences(this)

        initProtocolGroup()
        initSpeedGroup()
    }

    private fun initProtocolGroup() {
        val buttonId = if (prefs.useDSlipProtocol) R.id.dslip_button else R.id.ccnet_button
        findViewById<RadioButton>(buttonId).isChecked = true

        findViewById<RadioGroup>(R.id.protocolGroup).setOnCheckedChangeListener { _, checkedId ->
            prefs.useDSlipProtocol = (checkedId == R.id.dslip_button)
        }
    }

    private fun initSpeedGroup() {
        val buttonId = if (prefs.useDefaultSpeed) R.id.default_speed_button else R.id.megabit_speed_button
        findViewById<RadioButton>(buttonId).isChecked = true

        findViewById<RadioGroup>(R.id.speedGroup).setOnCheckedChangeListener { _, checkedId ->
            prefs.useDefaultSpeed = (checkedId == R.id.default_speed_button)
        }
    }
}
