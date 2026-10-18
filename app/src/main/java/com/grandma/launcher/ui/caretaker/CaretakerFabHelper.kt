package com.grandma.launcher.ui.caretaker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.grandma.launcher.R

/**
 * Attaches the caretaker button on screens other than Home.
 * When the overlay is allowed, the in-screen button hides and the floating
 * house and caretaker buttons are used instead.
 */
object CaretakerFabHelper {

    fun attach(activity: Activity, button: View) {
        if (Settings.canDrawOverlays(activity)) {
            button.visibility = View.GONE
            try {
                activity.startService(Intent(activity, CaretakerFloatingService::class.java))
            } catch (_: Exception) {
            }
            return
        }

        button.visibility = View.VISIBLE
        button.setOnClickListener {
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
                    activity.startActivity(Intent(activity, CaretakerHelpActivity::class.java))
                }
                .show()
        }

        val bottomMargin = activity.resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_bottom)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(button) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = bottomMargin + systemBars.bottom
                view.layoutParams = params
            }
            insets
        }
    }
}
