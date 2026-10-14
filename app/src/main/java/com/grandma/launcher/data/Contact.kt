package com.grandma.launcher.data

data class ContactPhone(
    val id: String,
    val label: String,
    val e164: String,
    val whatsapp: Boolean,
    val isDefault: Boolean
)

/**
 * A contact stored locally on the device.
 *
 * Photos live in app-private storage. [remoteId] matches the device-config contact id
 * when the contact is shared with caretakers. [phone] is the number the home screen dials.
 */
data class Contact(
    val id: Long,
    val name: String,
    val photoPath: String?,
    val phone: String,
    val isFavourite: Boolean = true,
    val remoteId: String? = null,
    val phones: List<ContactPhone> = emptyList(),
    val whatsapp: Boolean = false,
    val photoAssetId: String? = null,
    val nameClipId: String? = null
)
