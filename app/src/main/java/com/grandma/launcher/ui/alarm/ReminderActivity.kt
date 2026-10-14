package com.grandma.launcher.ui.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.appScope
import com.grandma.launcher.databinding.ActivityReminderBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceRegistrationRepository
import com.grandma.launcher.voice.VoiceManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Full-screen medicine reminder. Taken, snoozed, and missed are reported to the server. */
class ReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderBinding
    private var reminderId: String = ""
    private var label: String = ""
    private var reported = false
    private val voice by lazy { VoiceManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        reminderId = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()
        label = intent.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { getString(R.string.reminder_title) }
        binding.tvReminderLabel.text = label
        voice.speak(label, null)
        AlarmScheduler.rescheduleAll(this)

        binding.btnTaken.setOnClickListener { report("taken") }
        binding.btnSnooze.setOnClickListener {
            AlarmScheduler.snooze(this, reminderId.ifBlank { "alarm" }, label)
            report("snoozed")
        }
        binding.btnMissed.setOnClickListener { report("missed") }
    }

    override fun onDestroy() {
        if (!reported && reminderId.isNotBlank()) {
            reported = true
            val id = reminderId
            val tokens = DeviceRegistrationRepository(applicationContext)
            appScope.launch {
                val body = JSONObject().put("reminderId", id).put("status", "missed")
                ApiClient.deviceRequest("POST", "api/device/reminders/events", body, tokens)
            }
        }
        voice.shutdown()
        super.onDestroy()
    }

    private fun report(status: String) {
        if (reported) return
        reported = true
        if (reminderId.isBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val body = JSONObject().put("reminderId", reminderId).put("status", status)
            ApiClient.deviceRequest("POST", "api/device/reminders/events", body, DeviceRegistrationRepository(this@ReminderActivity))
            finish()
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_LABEL = "label"
    }
}
