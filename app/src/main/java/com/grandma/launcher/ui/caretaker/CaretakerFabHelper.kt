package com.grandma.launcher.ui.caretaker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.ui.home.HomeActivity

/**
 * Help stays bottom-start and home stays bottom-end. When the overlay is allowed,
 * those in-screen copies hide and the floating service draws only those two over
 * other apps. Gallery, YouTube, and the rest stay on the home screen. The first
 * time the overlay is missing, home asks for it.
 */
object CaretakerFabHelper {

    fun attach(
        activity: Activity,
        caretaker: View,
        home: View? = null
    ) {
        caretaker.setOnClickListener {
            activity.startActivity(Intent(activity, CaretakerHelpActivity::class.java))
        }
        syncOverlay(activity, caretaker, home)
    }

    fun syncOverlay(
        activity: Activity,
        caretaker: View,
        home: View? = null
    ) {
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

    /** Opens the system page for "display over other apps". True when a page opened. */
    fun askOverlayPermission(activity: Activity): Boolean {
        if (Settings.canDrawOverlays(activity)) return false
        val prefs = AppPreferences(activity)
        if (prefs.overlayPermissionAsked) return false
        val opened = openOverlaySettings(activity)
        if (opened) prefs.overlayPermissionAsked = true
        return opened
    }

    fun openOverlaySettings(activity: Activity): Boolean {
        val intents = listOf(
            overlaySettingsIntent(activity.packageName),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            miuiOverlayIntent(activity.packageName)
        )
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun overlaySettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))

    fun miuiOverlayIntent(packageName: String): Intent =
        Intent(MIUI_PERMISSION_EDITOR).apply {
            setClassName(MIUI_SECURITY_CENTER, MIUI_PERMISSION_ACTIVITY)
            putExtra(MIUI_PACKAGE_EXTRA, packageName)
        }

    fun goHome(activity: Activity) {
        activity.startActivity(
            Intent(activity, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private const val MIUI_PERMISSION_EDITOR = "miui.intent.action.APP_PERM_EDITOR"
    private const val MIUI_SECURITY_CENTER = "com.miui.securitycenter"
    private const val MIUI_PERMISSION_ACTIVITY = "com.miui.permcenter.permissions.PermissionsEditorActivity"
    private const val MIUI_PACKAGE_EXTRA = "extra_pkgname"
}
