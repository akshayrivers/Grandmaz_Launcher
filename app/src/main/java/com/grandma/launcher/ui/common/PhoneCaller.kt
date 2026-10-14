package com.grandma.launcher.ui.common

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * The single place that starts phone calls (contacts and SOS).
 * ACTION_CALL when CALL_PHONE is granted, otherwise ACTION_DIAL (number pre-filled, one
 * tap on the dialer's green button).
 */
object PhoneCaller {

    private const val TAG = "PhoneCaller"

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    fun buildCallIntent(number: String, canCallDirectly: Boolean): Intent =
        Intent(
            if (canCallDirectly) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            Uri.fromParts("tel", number, null)
        )

    /** Returns false if no app could handle the call at all. */
    fun placeCall(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        val direct = hasCallPermission(context)
        return try {
            context.startActivity(callIntent(context, number, direct))
            true
        } catch (e: SecurityException) {
            dial(context, number)
        } catch (e: ActivityNotFoundException) {
            if (direct) dial(context, number) else false
        }
    }

    private fun dial(context: Context, number: String): Boolean = try {
        context.startActivity(callIntent(context, number, canCallDirectly = false))
        true
    } catch (e: Exception) {
        Log.e(TAG, "No dialer available", e)
        false
    }

    private fun callIntent(context: Context, number: String, canCallDirectly: Boolean): Intent {
        val intent = buildCallIntent(number, canCallDirectly)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}

/**
 * Activity-scoped wrapper around [PhoneCaller] that asks for CALL_PHONE when missing and
 * then retries the pending call (direct call if granted, dialer if denied).
 * Must be created during Activity construction (it registers an activity-result launcher).
 */
class CallLauncher(private val activity: ComponentActivity) {

    private var pendingNumber: String? = null

    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingNumber?.let { PhoneCaller.placeCall(activity, it) }
            pendingNumber = null
        }

    /**
     * @param askPermission false for SOS: never put a permission dialog between grandma and
     * help — go straight to the dialer instead.
     */
    fun call(number: String, askPermission: Boolean = true) {
        if (PhoneCaller.hasCallPermission(activity) || !askPermission) {
            PhoneCaller.placeCall(activity, number)
            return
        }
        pendingNumber = number
        try {
            permissionLauncher.launch(Manifest.permission.CALL_PHONE)
        } catch (e: Exception) {
            pendingNumber = null
            PhoneCaller.placeCall(activity, number)
        }
    }
}
