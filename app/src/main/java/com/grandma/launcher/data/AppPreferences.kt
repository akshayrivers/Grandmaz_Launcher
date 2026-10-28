package com.grandma.launcher.data

import android.content.Context
import android.content.SharedPreferences
import com.grandma.launcher.BuildConfig

/**
 * App-wide settings stored in SharedPreferences.
 *
 * Keeping settings separate from contacts prefs so they
 * can be independently cleared or migrated in Phase 2.
 *
 * Secrets never live here in plaintext: the device private key is in Android Keystore,
 * the device token is stored AES-GCM encrypted (see network/DeviceTokenStore) and the
 * caretaker PIN only as a PBKDF2 hash (see data/PinManager).
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
     * Voice language chosen during setup: [LANGUAGE_HINDI] or [LANGUAGE_ENGLISH].
     */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, LANGUAGE_HINDI) ?: LANGUAGE_HINDI
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    /**
     * Primary caretaker email address.
     */
    var caretakerEmail: String
        get() = getCaretakerEmails().firstOrNull() ?: prefs.getString(KEY_CARETAKER_EMAIL, "") ?: ""
        set(value) {
            if (value.isNotBlank()) {
                addCaretakerEmail(value)
            } else {
                prefs.edit().putString(KEY_CARETAKER_EMAIL, "").apply()
            }
        }

    /**
     * List of all linked caretaker email addresses for multiple caretaker support.
     */
    fun getCaretakerEmails(): List<String> {
        val raw = prefs.getString(KEY_CARETAKER_EMAILS_LIST, "") ?: ""
        if (raw.isNotBlank()) {
            return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
        }
        val legacy = prefs.getString(KEY_CARETAKER_EMAIL, "") ?: ""
        return if (legacy.isNotBlank()) listOf(legacy.trim()) else emptyList()
    }

    /** Makes [email] the only (primary) caretaker email, e.g. when pairing again. */
    fun setPrimaryCaretakerEmail(email: String) {
        val trimmed = email.trim()
        prefs.edit()
            .putString(KEY_CARETAKER_EMAILS_LIST, trimmed)
            .putString(KEY_CARETAKER_EMAIL, trimmed)
            .apply()
    }

    fun addCaretakerEmail(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return
        val current = getCaretakerEmails().toMutableList()
        if (!current.contains(trimmed)) {
            current.add(trimmed)
            prefs.edit()
                .putString(KEY_CARETAKER_EMAILS_LIST, current.joinToString(","))
                .putString(KEY_CARETAKER_EMAIL, current.first())
                .apply()
        }
    }

    fun removeCaretakerEmail(email: String) {
        val trimmed = email.trim()
        val current = getCaretakerEmails().toMutableList()
        if (current.remove(trimmed)) {
            prefs.edit()
                .putString(KEY_CARETAKER_EMAILS_LIST, current.joinToString(","))
                .putString(KEY_CARETAKER_EMAIL, current.firstOrNull() ?: "")
                .apply()
        }
    }

    /**
     * Caretaker PIN as an encoded PBKDF2 hash (see [PinHasher]). Never the PIN itself.
     */
    var caretakerPinHash: String
        get() = prefs.getString(KEY_CARETAKER_PIN_HASH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CARETAKER_PIN_HASH, value).apply()

    /**
     * Plaintext PIN written by builds before Phase 0. Only read to migrate it into
     * [caretakerPinHash]; [clearLegacyPlaintextPin] deletes it.
     */
    val legacyPlaintextPin: String
        get() = prefs.getString(KEY_LEGACY_CARETAKER_PIN, "") ?: ""

    fun clearLegacyPlaintextPin() {
        prefs.edit().remove(KEY_LEGACY_CARETAKER_PIN).commit()
    }

    /**
     * Emergency number for the SOS button when no primary caretaker phone is known.
     * Defaults to 112 (India universal emergency number).
     * Caretakers can change it remotely (update_emergency_number command).
     */
    var emergencyNumber: String
        get() = prefs.getString(KEY_EMERGENCY_NUMBER, DEFAULT_EMERGENCY_NUMBER)
            ?: DEFAULT_EMERGENCY_NUMBER
        set(value) = prefs.edit().putString(KEY_EMERGENCY_NUMBER, value).apply()

    /**
     * Primary caretaker's phone (E.164) from GET /api/device/me. Empty if unknown.
     * SOS calls this number first.
     */
    var primaryCaretakerPhone: String
        get() = prefs.getString(KEY_PRIMARY_CARETAKER_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRIMARY_CARETAKER_PHONE, value).apply()

    /** Number the SOS button dials: primary caretaker, else the emergency number. */
    val sosNumber: String
        get() = primaryCaretakerPhone.ifBlank { emergencyNumber }

    /**
     * Duration in milliseconds before the caretaker FAB fades to idle state.
     * Default: 8 seconds.
     */
    var fabIdleDelayMs: Long
        get() = prefs.getLong(KEY_FAB_IDLE_DELAY, DEFAULT_FAB_IDLE_DELAY_MS)
        set(value) = prefs.edit().putLong(KEY_FAB_IDLE_DELAY, value).apply()

    /**
     * Unique Device ID for backend challenge-response registration.
     * Generated lazily; replaced by [rotateDeviceId] when the backend rejects our key.
     */
    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, "") ?: ""
            if (id.isEmpty()) {
                id = java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    fun rotateDeviceId(): String {
        val id = java.util.UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_DEVICE_ID, id)
            .putBoolean(KEY_IS_REGISTERED, false)
            .apply()
        return id
    }

    /**
     * Whether the backend has accepted our public key for [deviceId]. Only an optimisation
     * to skip /register; a 404 DEVICE_NOT_FOUND from /challenge resets it.
     */
    var isRegisteredWithBackend: Boolean
        get() = prefs.getBoolean(KEY_IS_REGISTERED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_REGISTERED, value).apply()

    /** AES-GCM ciphertext of the device token (base64 iv||ct). Use DeviceTokenStore. */
    var deviceTokenCiphertext: String
        get() = prefs.getString(KEY_DEVICE_TOKEN_CIPHERTEXT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN_CIPHERTEXT, value).apply()

    var deviceTokenExpiresAtMs: Long
        get() = prefs.getLong(KEY_DEVICE_TOKEN_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_DEVICE_TOKEN_EXPIRES_AT, value).apply()

    // ── Pairing ──────────────────────────────────────────────────────────────

    /** True once the backend confirmed a primary caretaker (invitation accepted or /me). */
    var isPaired: Boolean
        get() = prefs.getBoolean(KEY_IS_PAIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PAIRED, value).apply()

    var pendingInvitationId: String
        get() = prefs.getString(KEY_PENDING_INVITATION_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_INVITATION_ID, value).apply()

    var pendingPairingCode: String
        get() = prefs.getString(KEY_PENDING_PAIRING_CODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_PAIRING_CODE, value).apply()

    var pendingPairingExpiresAtMs: Long
        get() = prefs.getLong(KEY_PENDING_PAIRING_EXPIRES_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_PENDING_PAIRING_EXPIRES_AT, value).apply()

    fun clearPendingInvitation() {
        prefs.edit()
            .remove(KEY_PENDING_INVITATION_ID)
            .remove(KEY_PENDING_PAIRING_CODE)
            .remove(KEY_PENDING_PAIRING_EXPIRES_AT)
            .apply()
    }

    /**
     * Caretakers last seen from GET /api/device/me, so the helper screen can list them
     * with no connection. Each entry is email then role. No PIN.
     */
    fun linkedCaretakers(): List<Pair<String, String>> {
        val raw = prefs.getString(KEY_LINKED_CARETAKERS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.lastIndex) null
            else line.substring(0, tab) to line.substring(tab + 1)
        }.toList()
    }

    fun setLinkedCaretakers(people: List<Pair<String, String>>) {
        val raw = people.joinToString("\n") { (email, role) ->
            email.trim() + "\t" + role.trim()
        }
        prefs.edit().putString(KEY_LINKED_CARETAKERS, raw).apply()
    }

    /** Saved top-left of a floating button. Null until the helper drags it. */
    fun overlayPosition(help: Boolean): Pair<Int, Int>? {
        val xKey = if (help) KEY_OVERLAY_HELP_X else KEY_OVERLAY_HOME_X
        val yKey = if (help) KEY_OVERLAY_HELP_Y else KEY_OVERLAY_HOME_Y
        if (!prefs.contains(xKey) || !prefs.contains(yKey)) return null
        return prefs.getInt(xKey, 0) to prefs.getInt(yKey, 0)
    }

    /** True after the phone told the server it is being uninstalled, until this process is still here. */
    var uninstallNoticePending: Boolean
        get() = prefs.getBoolean(KEY_UNINSTALL_NOTICE_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_UNINSTALL_NOTICE_PENDING, value).apply()

    fun setOverlayPosition(help: Boolean, x: Int, y: Int) {
        val xKey = if (help) KEY_OVERLAY_HELP_X else KEY_OVERLAY_HOME_X
        val yKey = if (help) KEY_OVERLAY_HELP_Y else KEY_OVERLAY_HOME_Y
        prefs.edit().putInt(xKey, x).putInt(yKey, y).apply()
    }

    fun clearPairingState() {
        clearPendingInvitation()
        prefs.edit()
            .putBoolean(KEY_IS_PAIRED, false)
            .remove(KEY_PRIMARY_CARETAKER_PHONE)
            .apply()
    }

    /**
     * FCM registration token. Firebase is not integrated yet, so this stays empty and
     * the push-token call is skipped.
     */
    var fcmToken: String
        get() = prefs.getString(KEY_FCM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    /** Message from a show_message command that has not been shown on screen yet. */
    var pendingCaretakerMessage: String
        get() = prefs.getString(KEY_PENDING_MESSAGE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_MESSAGE, value).apply()

    /** Recorded clip that belongs with [pendingCaretakerMessage], if the caretaker sent one. */
    var pendingCaretakerMessageClip: String
        get() = prefs.getString(KEY_PENDING_MESSAGE_CLIP, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PENDING_MESSAGE_CLIP, value).apply()

    fun stageCaretakerMessage(text: String, clipId: String?) {
        prefs.edit()
            .putString(KEY_PENDING_MESSAGE, text)
            .putString(KEY_PENDING_MESSAGE_CLIP, clipId.orEmpty())
            .apply()
    }

    fun clearPendingCaretakerMessage() {
        prefs.edit()
            .remove(KEY_PENDING_MESSAGE)
            .remove(KEY_PENDING_MESSAGE_CLIP)
            .apply()
    }

    /**
     * Last background state sync timestamp in millis.
     */
    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, value).apply()

    /** Cached device-config version. 0 means nothing has been saved on the server yet. */
    var configVersion: Int
        get() = prefs.getInt(KEY_CONFIG_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_CONFIG_VERSION, value).apply()

    /** Local caretaker edits that still need PUT /api/device/config. */
    var configDirty: Boolean
        get() = prefs.getBoolean(KEY_CONFIG_DIRTY, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIG_DIRTY, value).apply()

    var weatherCity: String
        get() = prefs.getString(KEY_WEATHER_CITY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEATHER_CITY, value).apply()

    var weatherMode: String
        get() = prefs.getString(KEY_WEATHER_MODE, "city") ?: "city"
        set(value) = prefs.edit().putString(KEY_WEATHER_MODE, value).apply()

    /** True after home has opened the display-over-other-apps page once. */
    var overlayPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ASKED, value).apply()

    var inactivityReturnSec: Int
        get() = prefs.getInt(KEY_INACTIVITY_RETURN_SEC, 60)
        set(value) = prefs.edit().putInt(KEY_INACTIVITY_RETURN_SEC, value).apply()

    var smsFallbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_FALLBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_SMS_FALLBACK, value).apply()

    /** users.id of the caretaker SOS should call, when the config names one. */
    var primaryCaretakerUserId: String
        get() = prefs.getString(KEY_PRIMARY_CARETAKER_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PRIMARY_CARETAKER_USER_ID, value).apply()

    /** E.164 numbers of linked caretakers, for the SOS SMS fallback. */
    var caretakerPhones: List<String>
        get() = (prefs.getString(KEY_CARETAKER_PHONES, "") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.startsWith("+") }
        set(value) = prefs.edit().putString(KEY_CARETAKER_PHONES, value.distinct().joinToString(",")).apply()

    var activeSosEventId: String
        get() = prefs.getString(KEY_ACTIVE_SOS_EVENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_SOS_EVENT, value).apply()

    /**
     * One-time upgrade from the pre-Phase-0 storage layout. The old RSA identity (private
     * key in plain prefs) is discarded: the backend refuses a new key for an existing
     * deviceId, so we also take a fresh deviceId and must be paired again.
     */
    fun migrateLegacySecurityState() {
        if (prefs.getInt(KEY_SECURITY_SCHEMA, 0) >= SECURITY_SCHEMA_VERSION) return
        val hadLegacyIdentity = prefs.contains(KEY_LEGACY_DEVICE_PRIVATE_KEY) ||
            prefs.contains(KEY_DEVICE_ID)
        val editor = prefs.edit()
            .remove(KEY_LEGACY_DEVICE_PRIVATE_KEY)
            .remove(KEY_LEGACY_DEVICE_PUBLIC_KEY)
            .remove(KEY_LEGACY_IS_DEVICE_VERIFIED)
            .remove(KEY_LEGACY_BACKEND_BASE_URL)
            .remove(KEY_LEGACY_CARETAKER_GOOGLE_ID)
            .remove(KEY_LEGACY_CARETAKER_NAME)
            .remove(KEY_LEGACY_CARETAKER_PHOTO_URL)
            .remove(KEY_DEVICE_TOKEN_CIPHERTEXT)
            .remove(KEY_DEVICE_TOKEN_EXPIRES_AT)
            .putBoolean(KEY_IS_REGISTERED, false)
            .putInt(KEY_SECURITY_SCHEMA, SECURITY_SCHEMA_VERSION)
        if (hadLegacyIdentity) {
            editor.remove(KEY_DEVICE_ID)
                .remove(KEY_IS_PAIRED)
                .remove(KEY_PENDING_INVITATION_ID)
                .remove(KEY_PENDING_PAIRING_CODE)
                .remove(KEY_PENDING_PAIRING_EXPIRES_AT)
                .remove(KEY_PRIMARY_CARETAKER_PHONE)
        }
        editor.commit()
    }

    /**
     * Debug-only. A helper turns this on from Record her words (long-press the
     * home clock, enter the caretaker PIN). Release builds ignore the stored
     * value so production help requests still go to the backend.
     */
    var localFlowEnabled: Boolean
        get() = BuildConfig.DEBUG && prefs.getBoolean(KEY_LOCAL_FLOW, false)
        set(value) {
            if (BuildConfig.DEBUG) prefs.edit().putBoolean(KEY_LOCAL_FLOW, value).apply()
        }

    /** When local flow is on, the next help tap is reported as a failure. */
    var localFlowFailNextHelp: Boolean
        get() = BuildConfig.DEBUG && prefs.getBoolean(KEY_LOCAL_FLOW_FAIL, false)
        set(value) {
            if (BuildConfig.DEBUG) prefs.edit().putBoolean(KEY_LOCAL_FLOW_FAIL, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "grandma_app_prefs"
        private const val KEY_IS_SETUP_COMPLETE = "is_setup_complete"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CARETAKER_EMAIL = "caretaker_email"
        private const val KEY_CARETAKER_EMAILS_LIST = "caretaker_emails_list"
        private const val KEY_CARETAKER_PIN_HASH = "caretaker_pin_hash"
        private const val KEY_EMERGENCY_NUMBER = "emergency_number"
        private const val KEY_PRIMARY_CARETAKER_PHONE = "primary_caretaker_phone"
        private const val KEY_FAB_IDLE_DELAY = "fab_idle_delay_ms"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_IS_REGISTERED = "is_registered_with_backend"
        private const val KEY_DEVICE_TOKEN_CIPHERTEXT = "device_token_ct"
        private const val KEY_DEVICE_TOKEN_EXPIRES_AT = "device_token_expires_at"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_PENDING_INVITATION_ID = "pending_invitation_id"
        private const val KEY_PENDING_PAIRING_CODE = "pending_pairing_code"
        private const val KEY_PENDING_PAIRING_EXPIRES_AT = "pending_pairing_expires_at"
        private const val KEY_LINKED_CARETAKERS = "linked_caretakers"
        private const val KEY_OVERLAY_HELP_X = "overlay_help_x"
        private const val KEY_OVERLAY_HELP_Y = "overlay_help_y"
        private const val KEY_OVERLAY_HOME_X = "overlay_home_x"
        private const val KEY_OVERLAY_HOME_Y = "overlay_home_y"
        private const val KEY_UNINSTALL_NOTICE_PENDING = "uninstall_notice_pending"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_PENDING_MESSAGE = "pending_caretaker_message"
        private const val KEY_PENDING_MESSAGE_CLIP = "pending_caretaker_message_clip"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        private const val KEY_CONFIG_VERSION = "config_version"
        private const val KEY_CONFIG_DIRTY = "config_dirty"
        private const val KEY_WEATHER_CITY = "weather_city"
        private const val KEY_WEATHER_MODE = "weather_mode"
        private const val KEY_OVERLAY_ASKED = "overlay_permission_asked"
        private const val KEY_INACTIVITY_RETURN_SEC = "inactivity_return_sec"
        private const val KEY_SMS_FALLBACK = "sms_fallback"
        private const val KEY_PRIMARY_CARETAKER_USER_ID = "primary_caretaker_user_id"
        private const val KEY_CARETAKER_PHONES = "caretaker_phones"
        private const val KEY_ACTIVE_SOS_EVENT = "active_sos_event_id"
        private const val KEY_SECURITY_SCHEMA = "security_schema_version"
        private const val KEY_LOCAL_FLOW = "local_flow_enabled"
        private const val KEY_LOCAL_FLOW_FAIL = "local_flow_fail_next_help"

        // Pre-Phase-0 keys: only read/removed by migrations.
        private const val KEY_LEGACY_CARETAKER_PIN = "caretaker_pin"
        private const val KEY_LEGACY_DEVICE_PUBLIC_KEY = "device_public_key"
        private const val KEY_LEGACY_DEVICE_PRIVATE_KEY = "device_private_key"
        private const val KEY_LEGACY_IS_DEVICE_VERIFIED = "is_device_verified"
        private const val KEY_LEGACY_BACKEND_BASE_URL = "backend_base_url"
        private const val KEY_LEGACY_CARETAKER_GOOGLE_ID = "caretaker_google_id"
        private const val KEY_LEGACY_CARETAKER_NAME = "caretaker_name"
        private const val KEY_LEGACY_CARETAKER_PHOTO_URL = "caretaker_photo_url"

        private const val SECURITY_SCHEMA_VERSION = 2

        const val LANGUAGE_HINDI = "hi"
        const val LANGUAGE_ENGLISH = "en"
        const val DEFAULT_EMERGENCY_NUMBER = "112"
        const val DEFAULT_FAB_IDLE_DELAY_MS = 8000L
        const val SOS_HOLD_DURATION_MS = 3000L
    }
}
