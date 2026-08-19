package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Handles posting Help Requests (general, sos, medical, tech_support) to the backend.
 */
class HelpRequestRepository(private val context: Context) {

    private val appPrefs = AppPreferences(context)

    enum class HelpType(val value: String) {
        GENERAL("general"),
        SOS("sos"),
        MEDICAL("medical"),
        TECH_SUPPORT("tech_support")
    }

    suspend fun createHelpRequest(
        title: String,
        description: String? = null,
        type: HelpType = HelpType.GENERAL
    ): ApiClient.Result<JSONObject> = withContext(Dispatchers.IO) {
        val baseUrl = appPrefs.backendBaseUrl
        val deviceId = appPrefs.deviceId

        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("title", title)
            if (!description.isNullOrBlank()) {
                put("description", description)
            }
            put("type", type.value)
        }

        ApiClient.postJson(baseUrl, "api/help-requests", body)
    }
}
