package com.grandma.launcher.data

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

/**
 * Writes a remotely added WhatsApp contact into the system address book so the
 * WhatsApp call intent can find them. Does nothing when WRITE_CONTACTS is not granted.
 */
object SystemContacts {

    fun ensureWhatsAppContact(context: Context, contact: Contact) {
        if (!contact.whatsapp) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val number = contact.phones.firstOrNull { it.whatsapp }?.e164 ?: contact.phone
        if (number.isBlank()) return
        val existing = context.contentResolver.query(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon().appendPath(number).build(),
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )
        val found = existing?.use { it.moveToFirst() } == true
        if (found) return

        val ops = arrayListOf<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )
        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (_: Exception) {
            // A missing permission or a provider refusal must not break config apply.
        }
    }
}
