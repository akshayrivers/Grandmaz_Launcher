package com.grandma.launcher.ui.alarm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.grandma.launcher.R
import com.grandma.launcher.ui.views.AlarmClockView
import java.util.Locale

/**
 * Adapter to list set alarms inside the AlarmActivity recycler view.
 * Utilizes the custom static AlarmClockView for each item row.
 */
class AlarmListAdapter(
    private var alarms: List<Alarm>,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onClick: (Alarm) -> Unit,
    private val onLongClick: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmListAdapter.AlarmViewHolder>() {

    fun updateAlarms(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(alarms[position])
    }

    override fun getItemCount(): Int = alarms.size

    inner class AlarmViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: MaterialCardView = itemView.findViewById(R.id.cardAlarm)
        private val clockDisplay: AlarmClockView = itemView.findViewById(R.id.alarmClockDisplay)
        private val tvTime: TextView = itemView.findViewById(R.id.tvAlarmTime)
        private val switchAlarm: SwitchMaterial = itemView.findViewById(R.id.switchAlarm)

        fun bind(alarm: Alarm) {
            clockDisplay.setTime(alarm.hour, alarm.minute, alarm.isEnabled)

            val displayHour = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
            val amPm = if (alarm.hour < 12) "AM" else "PM"
            tvTime.text = String.format(Locale.ENGLISH, "%02d:%02d %s", displayHour, alarm.minute, amPm)

            switchAlarm.setOnCheckedChangeListener(null)
            switchAlarm.isChecked = alarm.isEnabled
            switchAlarm.setOnCheckedChangeListener { _, isChecked ->
                onToggle(alarm, isChecked)
                clockDisplay.setTime(alarm.hour, alarm.minute, isChecked)
            }

            card.setOnClickListener { onClick(alarm) }
            card.setOnLongClickListener {
                onLongClick(alarm)
                true
            }
        }
    }
}
