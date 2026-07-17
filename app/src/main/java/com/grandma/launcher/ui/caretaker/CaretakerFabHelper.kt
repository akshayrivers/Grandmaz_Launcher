package com.grandma.launcher.ui.caretaker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
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

        if (Settings.canDrawOverlays(activity)) {
            // Overlay permission is granted. Hide the in-app FAB and ensure the service is running.
            fab.visibility = View.GONE
            try {
                activity.startService(Intent(activity, CaretakerFloatingService::class.java))
            } catch (_: Exception) {}
            return
        }

        // Fallback: show the in-app FAB if overlay permission is not granted.
        fab.visibility = View.VISIBLE
        fab.alpha = FAB_ACTIVE_ALPHA
        fab.setOnClickListener {
            AlertDialog.Builder(activity)
                .setTitle("Enable Floating Caretaker")
                .setMessage("Would you like to make the Caretaker icon float on top of other apps? This helps you ask for help at any time, from any app.")
                .setPositiveButton("Enable") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
                .setNegativeButton("Not Now") { _, _ ->
                    activity.startActivity(
                        Intent(activity, CaretakerHelpActivity::class.java)
                    )
                }
                .show()
        }

        // Apply bottom window insets to FAB margin to prevent 3-button nav overlap
        val fabBottomMargin = activity.resources.getDimensionPixelSize(com.grandma.launcher.R.dimen.fab_caretaker_margin_bottom)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            val systemBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            )
            val params = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = fabBottomMargin + systemBars.bottom
                view.layoutParams = params
            }
            insets
        }

        scheduleIdleFade(activity, fab, prefs.fabIdleDelayMs)
    }

    /**
     * Call from the Activity's dispatchTouchEvent to reset the idle timer.
     */
    fun onScreenTouch(activity: Activity, fab: FloatingActionButton) {
        if (fab.visibility != View.VISIBLE) return
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
            if (!activity.isFinishing && fab.visibility == View.VISIBLE) {
                fab.animate()
                    .alpha(FAB_IDLE_ALPHA)
                    .setDuration(FAB_FADE_DURATION_MS)
                    .start()
            }
        }, delayMs)
    }
}

