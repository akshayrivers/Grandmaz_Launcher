package com.grandma.launcher.data

import android.content.Context

/**
 * Caretaker PIN storage and verification. PBKDF2 is deliberately slow, so call
 * [verify] / [setPin] off the main thread.
 *
 * There is intentionally no remote reset: a forgotten PIN is reset on the phone itself
 * (clear app data and run setup again).
 */
class PinManager(private val appPrefs: AppPreferences) {

    constructor(context: Context) : this(AppPreferences(context))

    fun isPinSet(): Boolean {
        migrateLegacyPin()
        return appPrefs.caretakerPinHash.isNotEmpty()
    }

    fun setPin(pin: String) {
        appPrefs.caretakerPinHash = PinHasher.hash(pin)
        appPrefs.clearLegacyPlaintextPin()
    }

    fun verify(pin: String): Boolean {
        if (pin.isEmpty()) return false
        migrateLegacyPin()
        return PinHasher.verify(pin, appPrefs.caretakerPinHash)
    }

    /** Hashes a plaintext PIN left by an older build and deletes the plaintext. */
    fun migrateLegacyPin() {
        val legacy = appPrefs.legacyPlaintextPin
        if (legacy.isEmpty()) return
        if (appPrefs.caretakerPinHash.isEmpty() && PinHasher.isValidFormat(legacy)) {
            appPrefs.caretakerPinHash = PinHasher.hash(legacy)
        }
        appPrefs.clearLegacyPlaintextPin()
    }
}
