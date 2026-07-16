package com.grandma.launcher.ui.caretaker

import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.grandma.launcher.data.AppPreferences

/**
 * Attaches the caretaker FAB behaviour to any Activity.
 *
 * Every screen that shows the caretaker FAB uses this helper
 * so the fade logic and tap behaviour are defined in one place.
 *
 * Usage:
 *   CaretakerFabHelper.attach(this, binding.fabCaretaker)
 *
 * The Activity must also call dispatchTouchEvent through this helper
 * to reset the idle timer on any screen touch. The Activity layout
 * must include the FAB with id R.id.fabCaretaker.
 */
object CaretakerFabHelper {

    private const val FAB_ACTIVE_ALPHA = 1.0f
    private const val FAB_IDLE_ALPHA = 0.25f
    private const val FAB_FADE_DURATION_MS = 400L

    fun attach(activity: Activity, fab: FloatingActionButton) {
        val prefs = AppPreferences(activity)

        fab.alpha = FAB_ACTIVE_ALPHA
        fab.setOnClickListener {
            activity.startActivity(
                Intent(activity, CaretakerHelpActivity::class.java)
            )
        }

        scheduleIdleFade(activity, fab, prefs.fabIdleDelayMs)
    }

    /**
     * Call from the Activity's dispatchTouchEvent to reset the idle timer.
     */
    fun onScreenTouch(activity: Activity, fab: FloatingActionButton) {
        val prefs = AppPreferences(activity)
        fab.removeCallbacks(null)
        fab.animate()
            .alpha(FAB_ACTIVE_ALPHA)
            .setDuration(FAB_FADE_DURATION_MS / 2)
            .start()
        scheduleIdleFade(activity, fab, prefs.fabIdleDelayMs)
    }

    private fun scheduleIdleFade(activity: Activity, fab: FloatingActionButton, delayMs: Long) {
        fab.postDelayed({
            if (!activity.isFinishing) {
                fab.animate()
                    .alpha(FAB_IDLE_ALPHA)
                    .setDuration(FAB_FADE_DURATION_MS)
                    .start()
            }
        }, delayMs)
    }
}
