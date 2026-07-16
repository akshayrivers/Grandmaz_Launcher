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
     * Emergency number for the SOS button.
     * Defaults to 112 (India universal emergency number).
     * Caretaker can change this in Phase 2.
     */
    var emergencyNumber: String
        get() = prefs.getString(KEY_EMERGENCY_NUMBER, DEFAULT_EMERGENCY_NUMBER)
            ?: DEFAULT_EMERGENCY_NUMBER
        set(value) = prefs.edit().putString(KEY_EMERGENCY_NUMBER, value).apply()

    /**
     * Caretaker email address for help requests.
     * Empty string means the help button will show a "not configured" message.
     */
    var caretakerEmail: String
        get() = prefs.getString(KEY_CARETAKER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARETAKER_EMAIL, value).apply()

    /**
     * Duration in milliseconds before the caretaker FAB fades to idle state.
     * Default: 8 seconds.
     */
    var fabIdleDelayMs: Long
        get() = prefs.getLong(KEY_FAB_IDLE_DELAY, DEFAULT_FAB_IDLE_DELAY_MS)
        set(value) = prefs.edit().putLong(KEY_FAB_IDLE_DELAY, value).apply()

    companion object {
        private const val PREFS_NAME = "grandma_app_prefs"
        private const val KEY_EMERGENCY_NUMBER = "emergency_number"
        private const val KEY_CARETAKER_EMAIL = "caretaker_email"
        private const val KEY_FAB_IDLE_DELAY = "fab_idle_delay_ms"

        const val DEFAULT_EMERGENCY_NUMBER = "112"
        const val DEFAULT_FAB_IDLE_DELAY_MS = 8000L
        const val SOS_HOLD_DURATION_MS = 3000L
    }
}
