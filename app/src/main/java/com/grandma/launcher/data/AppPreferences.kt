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

    /**
     * Backend REST API Base URL.
     */
    var backendBaseUrl: String
        get() = prefs.getString(KEY_BACKEND_BASE_URL, DEFAULT_BACKEND_BASE_URL) ?: DEFAULT_BACKEND_BASE_URL
        set(value) = prefs.edit().putString(KEY_BACKEND_BASE_URL, value.trimEnd('/')).apply()

    /**
     * Unique Device ID for backend challenge-response registration.
     */
    var deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, "") ?: ""
            if (id.isEmpty()) {
                id = "device_" + java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    /**
     * Device RSA Public Key in PEM format.
     */
    var devicePublicKeyPem: String
        get() = prefs.getString(KEY_DEVICE_PUBLIC_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_PUBLIC_KEY, value).apply()

    /**
     * Device RSA Private Key in PEM format.
     */
    var devicePrivateKeyPem: String
        get() = prefs.getString(KEY_DEVICE_PRIVATE_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_PRIVATE_KEY, value).apply()

    /**
     * Verification status with backend.
     */
    var isDeviceVerified: Boolean
        get() = prefs.getBoolean(KEY_IS_DEVICE_VERIFIED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_DEVICE_VERIFIED, value).apply()

    /**
     * Last background state sync timestamp in millis.
     */
    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, value).apply()

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
        private const val KEY_BACKEND_BASE_URL = "backend_base_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_PUBLIC_KEY = "device_public_key"
        private const val KEY_DEVICE_PRIVATE_KEY = "device_private_key"
        private const val KEY_IS_DEVICE_VERIFIED = "is_device_verified"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"

        const val DEFAULT_BACKEND_BASE_URL = "http://10.0.2.2:3000"
        const val DEFAULT_EMERGENCY_NUMBER = "112"
        const val DEFAULT_FAB_IDLE_DELAY_MS = 8000L
        const val SOS_HOLD_DURATION_MS = 3000L
    }
}

