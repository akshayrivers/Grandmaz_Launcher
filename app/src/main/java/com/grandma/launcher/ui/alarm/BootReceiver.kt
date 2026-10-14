package com.grandma.launcher.ui.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores alarms and medicine reminders after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return
        AlarmScheduler.rescheduleAll(context)
    }
}
