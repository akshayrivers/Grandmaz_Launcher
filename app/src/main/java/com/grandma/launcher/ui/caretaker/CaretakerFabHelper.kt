package com.grandma.launcher.ui.caretaker

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.view.View
import com.grandma.launcher.ui.home.HomeActivity

/**
 * Caretaker (bottom start) and, when passed, the house (bottom end) sit above
 * the screen. A tap on the caretaker opens help. It does not ask grandma to
 * grant an overlay. If the overlay is already allowed, the in-screen buttons
 * hide and the floating service draws both.
 */
object CaretakerFabHelper {

    fun attach(activity: Activity, caretaker: View, home: View? = null) {
        caretaker.setOnClickListener {
            activity.startActivity(Intent(activity, CaretakerHelpActivity::class.java))
        }
        syncOverlay(activity, caretaker, home)
    }

    fun syncOverlay(activity: Activity, caretaker: View, home: View? = null) {
        if (Settings.canDrawOverlays(activity)) {
            caretaker.visibility = View.GONE
            home?.visibility = View.GONE
            try {
                activity.startService(Intent(activity, CaretakerFloatingService::class.java))
            } catch (_: Exception) {
            }
            return
        }
        caretaker.visibility = View.VISIBLE
        home?.visibility = View.VISIBLE
        activity.stopService(Intent(activity, CaretakerFloatingService::class.java))
    }

    fun goHome(activity: Activity) {
        activity.startActivity(
            Intent(activity, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }
}
