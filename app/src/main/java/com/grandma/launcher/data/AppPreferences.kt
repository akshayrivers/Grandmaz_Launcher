package com.grandma.launcher.data

import android.content.Context
import android.content.SharedPreferences

/**
 * App-wide settings stored in SharedPreferences.
 *
 * Keeping settings separate from contacts prefs so they
 * can be independently cleared or migrated in Phase 2.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether initial onboarding setup by caretaker has been completed.
     */
    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_IS_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SETUP_COMPLETE, value).apply()

    /**
     * Caretaker's Google Account ID / Sub.
     */
    var caretakerGoogleId: String
        get() = prefs.getString(KEY_CARETAKER_GOOGLE_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARETAKER_GOOGLE_ID, value).apply()

    /**
     * Caretaker's Display Name from Google Account.
     */
    var caretakerName: String
        get() = prefs.getString(KEY_CARETAKER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARETAKER_NAME, value).apply()

    /**
     * Caretaker email address for help requests.
     * Empty string means the help button will show a "not configured" message.
     */
    var caretakerEmail: String
        get() = prefs.getString(KEY_CARETAKER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARETAKER_EMAIL, value).apply()

    /**
     * Caretaker's Google profile photo URL.
     */
    var caretakerPhotoUrl: String
        get() = prefs.getString(KEY_CARETAKER_PHOTO_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARETAKER_PHOTO_URL, value).apply()

    /**
     * Caretaker 4-digit security PIN for unlocking app launcher settings on phone.
     */
    var caretakerPin: String
        get() = prefs.getString(KEY_CARETAKER_PIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARETAKER_PIN, value).apply()

    /**
     * Emergency number for the SOS button.
     * Defaults to 112 (India universal emergency number).
     * Caretaker can change this during initial setup or in Caretaker Settings.
     */
    var emergencyNumber: String
        get() = prefs.getString(KEY_EMERGENCY_NUMBER, DEFAULT_EMERGENCY_NUMBER)
            ?: DEFAULT_EMERGENCY_NUMBER
        set(value) = prefs.edit().putString(KEY_EMERGENCY_NUMBER, value).apply()

    /**
     * Duration in milliseconds before the caretaker FAB fades to idle state.
     * Default: 8 seconds.
     */
    var fabIdleDelayMs: Long
        get() = prefs.getLong(KEY_FAB_IDLE_DELAY, DEFAULT_FAB_IDLE_DELAY_MS)
        set(value) = prefs.edit().putLong(KEY_FAB_IDLE_DELAY, value).apply()

    fun verifyPin(pinInput: String): Boolean {
        if (caretakerPin.isEmpty()) return true
        return caretakerPin == pinInput
    }

    companion object {
        private const val PREFS_NAME = "grandma_app_prefs"
        private const val KEY_IS_SETUP_COMPLETE = "is_setup_complete"
        private const val KEY_CARETAKER_GOOGLE_ID = "caretaker_google_id"
        private const val KEY_CARETAKER_NAME = "caretaker_name"
        private const val KEY_CARETAKER_EMAIL = "caretaker_email"
        private const val KEY_CARETAKER_PHOTO_URL = "caretaker_photo_url"
        private const val KEY_CARETAKER_PIN = "caretaker_pin"
        private const val KEY_EMERGENCY_NUMBER = "emergency_number"
        private const val KEY_FAB_IDLE_DELAY = "fab_idle_delay_ms"

        const val DEFAULT_EMERGENCY_NUMBER = "112"
        const val DEFAULT_FAB_IDLE_DELAY_MS = 8000L
        const val SOS_HOLD_DURATION_MS = 3000L
    }
}

