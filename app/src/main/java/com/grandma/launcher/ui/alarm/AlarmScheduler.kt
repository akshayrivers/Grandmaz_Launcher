package com.grandma.launcher.ui.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.grandma.launcher.data.AppPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/** Schedules clock alarms and medicine reminders with [AlarmManager.setAlarmClock]. */
object AlarmScheduler {

    fun ensurePermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (manager.canScheduleExactAlarms()) return
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun schedule(context: Context, requestCode: Int, triggerAt: Long, reminderId: String, label: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = Intent(context, ReminderActivity::class.java).apply {
            putExtra(ReminderActivity.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderActivity.EXTRA_LABEL, label)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            show,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, pending), pending)
    }

    fun cancel(context: Context, requestCode: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, ReminderActivity::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        manager.cancel(pending)
        pending.cancel()
    }

    fun rescheduleAll(context: Context) {
        rescheduleUserAlarms(context)
        rescheduleMedicine(context)
    }

    fun rescheduleUserAlarms(context: Context) {
        val prefs = context.getSharedPreferences("grandma_alarms", Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString("alarms", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (!obj.optBoolean("isEnabled", true)) continue
            val id = obj.optLong("id")
            schedule(
                context,
                id.toInt(),
                nextTrigger(obj.optInt("hour"), obj.optInt("minute"), null),
                "",
                "Alarm"
            )
        }
    }

    fun rescheduleMedicine(context: Context) {
        val file = File(context.filesDir, "device-config.json")
        if (!file.exists()) return
        val reminders = JSONObject(file.readText()).optJSONArray("reminders") ?: return
        for (i in 0 until reminders.length()) {
            val item = reminders.optJSONObject(i) ?: continue
            if (!item.optBoolean("enabled", true)) continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val days = item.optJSONArray("days")
            val dayList = if (days == null) null else IntArray(days.length()) { days.optInt(it) }
            schedule(
                context,
                requestCode(id),
                nextTrigger(item.optInt("hour"), item.optInt("minute"), dayList),
                id,
                item.optString("label", "Medicine")
            )
        }
    }

    fun snooze(context: Context, reminderId: String, label: String) {
        schedule(context, requestCode(reminderId) + 50_000, System.currentTimeMillis() + 10 * 60 * 1000L, reminderId, label)
    }

    fun requestCode(reminderId: String): Int = reminderId.hashCode() and 0x0FFF_FFFF

    fun nextTrigger(hour: Int, minute: Int, days: IntArray?): Long {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (days == null || days.isEmpty()) {
            if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
            return calendar.timeInMillis
        }
        for (offset in 0..7) {
            val candidate = calendar.clone() as Calendar
            candidate.add(Calendar.DAY_OF_YEAR, offset)
            val dow = candidate.get(Calendar.DAY_OF_WEEK) - 1
            if (dow in days && candidate.timeInMillis > now) return candidate.timeInMillis
        }
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    fun inactivitySeconds(context: Context): Int = AppPreferences(context).inactivityReturnSec
}
