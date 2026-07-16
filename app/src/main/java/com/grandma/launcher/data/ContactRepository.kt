package com.grandma.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local contact storage using SharedPreferences + JSON.
 *
 * Why SharedPreferences and not Room/SQLite?
 * - Phase 1 has no backend and no complex queries
 * - The contact list is small (typically 5–15 contacts)
 * - Zero dependencies — no annotation processor, no migrations
 * - Easy to read/write in a single atomic commit
 *
 * The contacts are serialised as a JSON array in prefs.
 * Photos live in the app's private files directory and are
 * referenced by path — they are not embedded in prefs.
 */
class ContactRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Contact> {
        val json = prefs.getString(KEY_CONTACTS, "[]") ?: "[]"
        return parseContacts(json)
    }

    fun getFavourites(): List<Contact> =
        getAll().filter { it.isFavourite }

    fun save(contact: Contact) {
        val all = getAll().toMutableList()
        val existingIndex = all.indexOfFirst { it.id == contact.id }
        if (existingIndex >= 0) {
            all[existingIndex] = contact
        } else {
            all.add(contact)
        }
        persist(all)
    }

    fun remove(contactId: Long) {
        val all = getAll().toMutableList()
        all.removeAll { it.id == contactId }
        persist(all)
    }

    fun setFavourite(contactId: Long, favourite: Boolean) {
        val all = getAll().map { contact ->
            if (contact.id == contactId) contact.copy(isFavourite = favourite)
            else contact
        }
        persist(all)
    }

    // ── Serialisation ────────────────────────────────────────────────────────

    private fun parseContacts(json: String): List<Contact> {
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Contact(
                    id = obj.getLong(FIELD_ID),
                    name = obj.getString(FIELD_NAME),
                    photoPath = obj.optString(FIELD_PHOTO, null),
                    phone = obj.getString(FIELD_PHONE),
                    isFavourite = obj.optBoolean(FIELD_FAVOURITE, true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(contacts: List<Contact>) {
        val array = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject().apply {
                put(FIELD_ID, contact.id)
                put(FIELD_NAME, contact.name)
                contact.photoPath?.let { put(FIELD_PHOTO, it) }
                put(FIELD_PHONE, contact.phone)
                put(FIELD_FAVOURITE, contact.isFavourite)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "grandma_contacts"
        private const val KEY_CONTACTS = "contacts"
        private const val FIELD_ID = "id"
        private const val FIELD_NAME = "name"
        private const val FIELD_PHOTO = "photo"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_FAVOURITE = "favourite"
    }
}
