package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device stand-in for POST /api/device/help-requests.
 *
 * A helper turns it on from "Record her words": long-press the home clock,
 * enter the caretaker PIN, then "Keep help requests on this phone".
 * Release builds ignore that switch and always use the deployed device API.
 * The caretaker PIN is never written here.
 */
internal object LocalHelpStore {

    data class Entry(val type: String, val atMillis: Long)

    fun isActive(context: Context): Boolean = AppPreferences(context).localFlowEnabled

    fun submit(
        context: Context,
        title: String,
        description: String?,
        type: HelpRequestRepository.HelpType
    ): ApiClient.Result<Any?> {
        val prefs = AppPreferences(context)
        if (prefs.localFlowFailNextHelp) {
            return ApiClient.Result.Error(statusCode = 0, message = "local", code = "local_rejected")
        }
        val body = HelpRequestRepository.buildBody(title, description, type)
        append(context, body.getString("type"))
        return ApiClient.Result.Success(null)
    }

    fun entries(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]")
            .orEmpty()
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(Entry(item.optString("type"), item.optLong("at")))
            }
        }
    }

    private fun append(context: Context, type: String) {
        val all = entries(context).takeLast(MAX_ENTRIES - 1) + Entry(type, System.currentTimeMillis())
        val array = JSONArray()
        all.forEach { entry ->
            array.put(JSONObject().put("type", entry.type).put("at", entry.atMillis))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .commit()
    }

    private const val PREFS = "grandma_app_prefs"
    private const val KEY = "local_help_log"
    private const val MAX_ENTRIES = 20
}
