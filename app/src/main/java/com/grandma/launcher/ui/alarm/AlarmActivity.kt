package com.grandma.launcher.ui.alarm

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.grandma.launcher.R
import com.grandma.launcher.databinding.ActivityAlarmBinding
import java.util.Calendar
import java.util.Locale

/**
 * Data class representing a simple alarm set by the user.
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true
)

/**
 * Screen where Grandma and helpers manage alarms.
 * Displays lists of alarms, provides an analog clock setting UI,
 * and schedules triggers via the system AlarmManager.
 */
class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var adapter: AlarmListAdapter
    private var alarmList = mutableListOf<Alarm>()

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAlarmBack.setOnClickListener { finish() }

        alarmList.addAll(loadAlarms())
        updateEmptyState()

        adapter = AlarmListAdapter(
            alarmList,
            onToggle = { alarm, isChecked -> toggleAlarm(alarm, isChecked) },
            onClick = { alarm -> showEditAlarmDialog(alarm) },
            onLongClick = { alarm -> showDeleteConfirmDialog(alarm) }
        )
        binding.rvAlarms.layoutManager = LinearLayoutManager(this)
        binding.rvAlarms.adapter = adapter

        binding.btnAddNewAlarm.setOnClickListener {
            showAddAlarmDialog()
        }

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        val topMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_top)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_bottom)

        ViewCompat.setOnApplyWindowInsetsListener(binding.alarmRootLayout) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                horizontalMargin,
                topMargin + systemBars.top,
                horizontalMargin,
                bottomMargin
            )
            val btnParams = binding.btnAddNewAlarm.layoutParams as? ViewGroup.MarginLayoutParams
            if (btnParams != null) {
                btnParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.space_md) + systemBars.bottom
                binding.btnAddNewAlarm.layoutParams = btnParams
            }
            insets
        }
    }

    private fun updateEmptyState() {
        if (alarmList.isEmpty()) {
            binding.tvNoAlarms.visibility = View.VISIBLE
            binding.rvAlarms.visibility = View.GONE
        } else {
            binding.tvNoAlarms.visibility = View.GONE
            binding.rvAlarms.visibility = View.VISIBLE
        }
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private fun showAddAlarmDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val settingsView = AlarmSettingsView(this)
        settingsView.setTime(7, 0, true)

        dialog.setContentView(settingsView)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        settingsView.onCancelClick = { dialog.dismiss() }
        settingsView.onSaveClick = { hr, min, am ->
            val hour24 = convertTo24Hour(hr, am)
            val newAlarm = Alarm(
                id = System.currentTimeMillis(),
                hour = hour24,
                minute = min,
                isEnabled = true
            )
            alarmList.add(newAlarm)
            saveAndRefresh()
            scheduleAlarmInSystem(newAlarm)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showEditAlarmDialog(alarm: Alarm) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val settingsView = AlarmSettingsView(this)
        val isAm = alarm.hour < 12
        val hr12 = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
        settingsView.setTime(hr12, alarm.minute, isAm)

        dialog.setContentView(settingsView)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        settingsView.onCancelClick = { dialog.dismiss() }
        settingsView.onSaveClick = { hr, min, am ->
            val hour24 = convertTo24Hour(hr, am)
            val updated = alarm.copy(hour = hour24, minute = min, isEnabled = true)
            val index = alarmList.indexOfFirst { it.id == alarm.id }
            if (index != -1) {
                alarmList[index] = updated
                saveAndRefresh()
                scheduleAlarmInSystem(updated)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showDeleteConfirmDialog(alarm: Alarm) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_call_confirm, null)
        dialog.setContentView(view)

        val ivPhoto = view.findViewById<android.widget.ImageView>(R.id.ivConfirmPhoto)
        ivPhoto.setImageResource(android.R.drawable.ic_lock_idle_alarm)
        ivPhoto.setColorFilter(getColor(R.color.color_sos_red))

        val tvTitle = view.findViewById<TextView>(R.id.tvConfirmTitle)
        val displayHour = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
        val amPm = if (alarm.hour < 12) "AM" else "PM"
        val timeString = String.format(Locale.ENGLISH, "%02d:%02d %s", displayHour, alarm.minute, amPm)
        tvTitle.text = "Delete alarm for\n$timeString?"

        val btnYes = view.findViewById<Button>(R.id.btnConfirmYes)
        btnYes.text = "YES, DELETE"
        btnYes.setBackgroundColor(getColor(R.color.color_sos_red))
        btnYes.setOnClickListener {
            cancelAlarmInSystem(alarm)
            alarmList.remove(alarm)
            saveAndRefresh()
            dialog.dismiss()
        }

        val btnNo = view.findViewById<Button>(R.id.btnConfirmNo)
        btnNo.text = "NO, GO BACK"
        btnNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }

    // ── Preferences Persistence ──────────────────────────────────────────────

    private fun loadAlarms(): List<Alarm> {
        val prefs = getSharedPreferences("grandma_alarms", MODE_PRIVATE)
        val json = prefs.getString("alarms", "[]") ?: "[]"
        val list = mutableListOf<Alarm>()
        try {
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Alarm(
                        id = obj.getLong("id"),
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveAlarms(list: List<Alarm>) {
        val prefs = getSharedPreferences("grandma_alarms", MODE_PRIVATE)
        val arr = org.json.JSONArray()
        for (alarm in list) {
            val obj = org.json.JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("isEnabled", alarm.isEnabled)
            }
            arr.put(obj)
        }
        prefs.edit().putString("alarms", arr.toString()).apply()
    }

    private fun saveAndRefresh() {
        saveAlarms(alarmList)
        adapter.updateAlarms(alarmList)
        updateEmptyState()
    }

    private fun toggleAlarm(alarm: Alarm, isChecked: Boolean) {
        val updated = alarm.copy(isEnabled = isChecked)
        val index = alarmList.indexOfFirst { it.id == alarm.id }
        if (index != -1) {
            alarmList[index] = updated
            saveAlarms(alarmList)
            if (isChecked) {
                scheduleAlarmInSystem(updated)
            } else {
                cancelAlarmInSystem(updated)
            }
        }
    }

    private fun convertTo24Hour(hour12: Int, isAm: Boolean): Int {
        return if (isAm) {
            if (hour12 == 12) 0 else hour12
        } else {
            if (hour12 == 12) 12 else hour12 + 12
        }
    }

    // ── System Alarm scheduling ──────────────────────────────────────────────

    private fun scheduleAlarmInSystem(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } catch (_: Exception) {}
    }

    private fun cancelAlarmInSystem(alarm: Alarm) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
