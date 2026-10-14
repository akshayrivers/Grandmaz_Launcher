package com.grandma.launcher.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.grandma.launcher.data.Contact

/**
 * Opens a WhatsApp video call for one person. If that intent is refused, tries a
 * WhatsApp voice call, then the normal phone dialer.
 */
object WhatsAppCaller {

    fun call(context: Context, contact: Contact, fallback: () -> Unit) {
        val number = (contact.phones.firstOrNull { it.whatsapp }?.e164 ?: contact.phone)
            .filter { it.isDigit() }
        if (number.isBlank() || !contact.whatsapp) {
            fallback()
            return
        }
        val video = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?video=1")).apply {
            setPackage(WHATSAPP)
        }
        val voice = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")).apply {
            setPackage(WHATSAPP)
        }
        when {
            tryStart(context, video) -> Unit
            tryStart(context, voice) -> Unit
            else -> fallback()
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private const val WHATSAPP = "com.whatsapp"
}
