package com.grandma.launcher.ui.alarm

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import com.grandma.launcher.R
import com.grandma.launcher.ui.views.AnalogTimePickerView

/**
 * A compound view wrapping the AnalogTimePickerView along with large accessible
 * Save and Cancel buttons for configuring an alarm.
 */
class AlarmSettingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val timePicker: AnalogTimePickerView
    private val btnCancel: Button
    private val btnSave: Button

    var onSaveClick: ((hour: Int, minute: Int, isAm: Boolean) -> Unit)? = null
    var onCancelClick: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_alarm_settings, this, true)

        timePicker = findViewById(R.id.timePicker)
        btnCancel = findViewById(R.id.btnSettingsCancel)
        btnSave = findViewById(R.id.btnSettingsSave)

        btnCancel.setOnClickListener {
            onCancelClick?.invoke()
        }

        btnSave.setOnClickListener {
            onSaveClick?.invoke(
                timePicker.currentHour,
                timePicker.currentMinute,
                timePicker.isAm
            )
        }
    }

    fun setTime(hour: Int, minute: Int, isAm: Boolean) {
        timePicker.currentHour = hour
        timePicker.currentMinute = minute
        timePicker.isAm = isAm
    }
}
