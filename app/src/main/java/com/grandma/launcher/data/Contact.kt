package com.grandma.launcher.data

/**
 * A contact stored locally on the device.
 *
 * Phase 1: stored in SharedPreferences as JSON, photo as a file
 * in the app's private internal storage. No backend, no cloud.
 *
 * Phase 2: this model will gain a syncId field when the caretaker
 * portal can push contact changes remotely.
 *
 * @param id        Unique identifier (timestamp of creation)
 * @param name      Display name — typed by caretaker during setup.
 *                  Phase 3 will add a voiceNamePath for recorded audio.
 * @param photoPath Absolute path to photo file in app's private storage.
 *                  Null if no photo set (shows initials placeholder).
 * @param phone     Phone number string. Stored as-is, dialled via Intent.
 * @param isFavourite Whether to show on the home screen (max 4 shown).
 */
data class Contact(
    val id: Long,
    val name: String,
    val photoPath: String?,
    val phone: String,
    val isFavourite: Boolean = true
)
