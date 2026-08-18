package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Handles sending magic-link email invitations for caretaker registration.
 */
class CaretakerInvitationRepository(private val context: Context) {

    private val appPrefs = AppPreferences(context)

    suspend fun sendCaretakerInvitation(email: String, deviceName: String = "Grandma's Phone"): ApiClient.Result<JSONObject> = withContext(Dispatchers.IO) {
        val baseUrl = appPrefs.backendBaseUrl
        val deviceId = appPrefs.deviceId

        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("email", email)
            put("deviceName", deviceName)
        }

        ApiClient.postJson(baseUrl, "api/invitations", body)
    }
}
