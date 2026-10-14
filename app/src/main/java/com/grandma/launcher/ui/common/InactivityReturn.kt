package com.grandma.launcher.ui.common

import android.app.Activity
import android.content.Intent
import android.view.View
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.ui.home.HomeActivity

/** Returns to the home screen after the caretaker-configured quiet period. */
class InactivityReturn(private val activity: Activity, private val root: View) {

    private val goHome = Runnable {
        activity.startActivity(
            Intent(activity, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        activity.finish()
    }

    fun arm() {
        root.removeCallbacks(goHome)
        val seconds = AppPreferences(activity).inactivityReturnSec.coerceIn(15, 600)
        root.postDelayed(goHome, seconds * 1000L)
    }

    fun stop() {
        root.removeCallbacks(goHome)
    }
}
