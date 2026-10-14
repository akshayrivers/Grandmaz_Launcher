package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactPhone
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * GET/PUT /api/device/config. The document is cached on disk so grandma's home still
 * works offline. Local caretaker edits set [AppPreferences.configDirty] and are sent
 * with If-Match on the next sync.
 */
class DeviceConfigRepository(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context)
) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)
    private val contacts = ContactRepository(appContext)
    private val cacheFile = File(appContext.filesDir, "device-config.json")

    suspend fun sync(): ApiClient.Result<Unit> {
        if (prefs.configDirty) {
            when (val pushed = push()) {
                is ApiClient.Result.Error -> return pushed
                is ApiClient.Result.Success -> prefs.configDirty = false
            }
        }
        return pull()
    }

    /** Folds the on-device contact list and weather city into the cached document. */
    fun noteLocalEdits() {
        val config = readConfig()
        val contactsJson = contactsToJson(contacts.getAll())
        config.put("contacts", contactsJson)
        val home = config.optJSONObject("home") ?: JSONObject()
        val pinned = JSONArray()
        for (i in 0 until contactsJson.length()) {
            val contact = contactsJson.optJSONObject(i) ?: continue
            if (contact.optBoolean("pinned")) pinned.put(contact.getString("id"))
        }
        home.put("pinnedContactIds", pinned)
        val weather = home.optJSONObject("weather") ?: JSONObject()
        weather.put("mode", prefs.weatherMode.ifBlank { "city" })
        weather.put("city", prefs.weatherCity.ifBlank { JSONObject.NULL })
        if (!weather.has("lat")) weather.put("lat", JSONObject.NULL)
        if (!weather.has("lon")) weather.put("lon", JSONObject.NULL)
        home.put("weather", weather)
        home.put("inactivityReturnSec", prefs.inactivityReturnSec.coerceIn(15, 600))
        config.put("home", home)
        writeConfig(config)
        prefs.configDirty = true
    }

    fun setWeatherCity(city: String) {
        prefs.weatherCity = city.trim()
        prefs.weatherMode = "city"
        noteLocalEdits()
    }

    private suspend fun pull(): ApiClient.Result<Unit> {
        return when (val result = ApiClient.deviceRequest("GET", "api/device/config", null, tokens)) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> {
                val body = result.data as? JSONObject ?: return ApiClient.Result.Error(
                    ApiClient.INVALID_RESPONSE, "Config response was empty", "INVALID_RESPONSE"
                )
                val version = body.optInt("version", 0)
                val config = body.optJSONObject("config") ?: return ApiClient.Result.Error(
                    ApiClient.INVALID_RESPONSE, "Config document was missing", "INVALID_RESPONSE"
                )
                if (!prefs.configDirty) {
                    prefs.configVersion = version
                    writeConfig(config)
                    apply(config)
                }
                ApiClient.Result.Success(Unit)
            }
        }
    }

    private suspend fun push(): ApiClient.Result<Unit> {
        val config = readConfig()
        val result = ApiClient.deviceRequest(
            "PUT",
            "api/device/config",
            config,
            tokens,
            mapOf("If-Match" to prefs.configVersion.toString())
        )
        return when (result) {
            is ApiClient.Result.Error -> {
                if (result.code == "CONFIG_VERSION_CONFLICT") {
                    val serverVersion = result.details?.optInt("currentVersion", prefs.configVersion)
                    if (serverVersion != null) prefs.configVersion = serverVersion
                }
                result
            }
            is ApiClient.Result.Success -> {
                val body = result.data as? JSONObject
                if (body != null) {
                    prefs.configVersion = body.optInt("version", prefs.configVersion)
                    body.optJSONObject("config")?.let { writeConfig(it); apply(it) }
                }
                ApiClient.Result.Success(Unit)
            }
        }
    }

    private fun apply(config: JSONObject) {
        val emergency = config.optJSONObject("emergency")
        prefs.primaryCaretakerUserId = emergency?.optString("primaryCaretakerUserId").orEmpty()
            .takeUnless { it == "null" } ?: ""
        prefs.smsFallbackEnabled = emergency?.optBoolean("smsFallback", true) ?: true

        val home = config.optJSONObject("home")
        val weather = home?.optJSONObject("weather")
        prefs.weatherMode = weather?.optString("mode").takeUnless { it.isNullOrBlank() } ?: "city"
        prefs.weatherCity = nullableString(weather, "city") ?: ""
        prefs.inactivityReturnSec = home?.optInt("inactivityReturnSec", 60)?.coerceIn(15, 600) ?: 60
        com.grandma.launcher.ui.alarm.AlarmScheduler.rescheduleMedicine(appContext)

        val remoteContacts = config.optJSONArray("contacts") ?: JSONArray()
        val existing = contacts.getAll()
        val byRemote = existing.mapNotNull { contact -> contact.remoteId?.let { it to contact } }.toMap()
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<Contact>()
        for (i in 0 until remoteContacts.length()) {
            val obj = remoteContacts.optJSONObject(i) ?: continue
            val remoteId = obj.optString("id")
            if (remoteId.isBlank()) continue
            seen += remoteId
            val phones = parsePhones(obj.optJSONArray("phones"))
            val dial = phones.firstOrNull { it.isDefault }?.e164 ?: phones.firstOrNull()?.e164 ?: ""
            val previous = byRemote[remoteId]
            merged += Contact(
                id = previous?.id ?: stableId(remoteId),
                name = obj.optString("name").ifBlank { previous?.name ?: "" },
                photoPath = previous?.photoPath,
                phone = dial.ifBlank { previous?.phone ?: "" },
                isFavourite = obj.optBoolean("pinned"),
                remoteId = remoteId,
                phones = phones,
                whatsapp = phones.any { it.whatsapp },
                photoAssetId = nullableString(obj, "photoAssetId"),
                nameClipId = nullableString(obj, "nameClipId")
            )
        }
        contacts.replaceAll(merged + existing.filter { it.remoteId == null || it.remoteId !in seen })
        contacts.getAll().forEach { contact ->
            com.grandma.launcher.data.SystemContacts.ensureWhatsAppContact(appContext, contact)
        }
    }

    private fun contactsToJson(local: List<Contact>): JSONArray {
        val array = JSONArray()
        val included = local.mapNotNull { contact ->
            val e164 = contact.phones.firstOrNull { it.isDefault }?.e164
                ?: contact.phones.firstOrNull()?.e164
                ?: contact.phone
            if (!E164.matches(e164)) null else contact to e164
        }
        val pinnedIds = included.filter { it.first.isFavourite }.take(4).map { (contact, _) ->
            contact.remoteId ?: "c${contact.id}"
        }.toSet()
        val updated = local.toMutableList()
        included.forEachIndexed { index, (contact, e164) ->
            val remoteId = contact.remoteId ?: "c${contact.id}"
            if (contact.remoteId == null) {
                val at = updated.indexOfFirst { it.id == contact.id }
                if (at >= 0) updated[at] = contact.copy(remoteId = remoteId)
            }
            val phones = if (contact.phones.isNotEmpty()) contact.phones else listOf(
                ContactPhone(id = "p${contact.id}", label = "", e164 = e164, whatsapp = contact.whatsapp, isDefault = true)
            )
            array.put(JSONObject().apply {
                put("id", remoteId)
                put("name", contact.name)
                put("phones", JSONArray().apply {
                    phones.filter { E164.matches(it.e164) }.forEach { phone ->
                        put(JSONObject().apply {
                            put("id", phone.id)
                            put("label", phone.label)
                            put("e164", phone.e164)
                            put("whatsapp", phone.whatsapp)
                            put("isDefault", phone.isDefault)
                        })
                    }
                })
                put("order", index)
                put("pinned", remoteId in pinnedIds)
                put("photoAssetId", contact.photoAssetId ?: JSONObject.NULL)
                put("nameClipId", contact.nameClipId ?: JSONObject.NULL)
            })
        }
        contacts.replaceAll(updated)
        return array
    }

    private fun readConfig(): JSONObject {
        val text = if (cacheFile.exists()) cacheFile.readText() else ""
        if (text.isNotBlank()) {
            return try {
                JSONObject(text)
            } catch (_: Exception) {
                defaultConfig()
            }
        }
        return defaultConfig()
    }

    private fun writeConfig(config: JSONObject) {
        cacheFile.writeText(config.toString())
    }

    private fun defaultConfig(): JSONObject = JSONObject().apply {
        put("locale", "hi")
        put("contacts", JSONArray())
        put("emergency", JSONObject().apply {
            put("primaryCaretakerUserId", JSONObject.NULL)
            put("smsFallback", true)
        })
        put("home", JSONObject().apply {
            put("pinnedContactIds", JSONArray())
            put("weather", JSONObject().apply {
                put("mode", "city")
                put("city", JSONObject.NULL)
                put("lat", JSONObject.NULL)
                put("lon", JSONObject.NULL)
            })
            put("inactivityReturnSec", 60)
        })
        put("reminders", JSONArray())
        put("voice", JSONObject().apply {
            put("clips", JSONArray())
            put("ttsRate", 0.9)
        })
        put("apps", JSONObject().apply { put("visible", JSONArray()) })
    }

    private fun parsePhones(array: JSONArray?): List<ContactPhone> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val e164 = obj.optString("e164")
            if (!E164.matches(e164)) return@mapNotNull null
            ContactPhone(
                id = obj.optString("id").ifBlank { "p$i" },
                label = obj.optString("label"),
                e164 = e164,
                whatsapp = obj.optBoolean("whatsapp"),
                isDefault = obj.optBoolean("isDefault")
            )
        }
    }

    private fun nullableString(obj: JSONObject?, key: String): String? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.optString(key).takeIf { it.isNotBlank() }
    }

    private fun stableId(remoteId: String): Long {
        val hash = remoteId.hashCode().toLong() and 0x7fffffffL
        return if (hash == 0L) 1L else hash
    }

    companion object {
        private val E164 = Regex("^\\+[1-9]\\d{6,14}$")
    }
}
