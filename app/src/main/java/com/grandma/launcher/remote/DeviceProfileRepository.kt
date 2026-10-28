package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import org.json.JSONObject

/**
 * GET /api/device/me (caches the primary caretaker's phone for SOS) and the FCM push token.
 */
class DeviceProfileRepository(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context)
) {

    private val appPrefs = AppPreferences(context)

    data class LinkedCaretaker(val email: String, val role: String)

    /** Refreshes [AppPreferences.primaryCaretakerPhone] and [AppPreferences.isPaired]. */
    suspend fun refreshProfile(): ApiClient.Result<Unit> {
        return when (val result = loadCaretakers()) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> ApiClient.Result.Success(Unit)
        }
    }

    /** Emails and roles from GET /api/device/me. Cached for the helper screen. No PIN. */
    suspend fun linkedCaretakers(): ApiClient.Result<List<LinkedCaretaker>> = loadCaretakers()

    private suspend fun loadCaretakers(): ApiClient.Result<List<LinkedCaretaker>> {
        return when (val result = ApiClient.deviceRequest("GET", "api/device/me", null, tokens)) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> {
                val caretakers = (result.data as? JSONObject)?.optJSONArray("caretakers")
                var primary: JSONObject? = null
                val wantedId = appPrefs.primaryCaretakerUserId
                val linked = ArrayList<LinkedCaretaker>()
                if (caretakers != null) {
                    for (i in 0 until caretakers.length()) {
                        val c = caretakers.optJSONObject(i) ?: continue
                        val email = c.optString("email")
                        if (email.isNotBlank()) linked.add(LinkedCaretaker(email, c.optString("role")))
                        if (wantedId.isNotBlank() && c.optString("caretakerId") == wantedId) {
                            primary = c
                        } else if (primary == null && c.optString("role") == ROLE_PRIMARY) {
                            primary = c
                        }
                    }
                }
                appPrefs.isPaired = primary != null
                appPrefs.primaryCaretakerPhone =
                    if (primary == null || primary.isNull("phoneE164")) "" else primary.optString("phoneE164")
                appPrefs.caretakerPhones = SosCoordinator.phonesFromCaretakers(caretakers)
                appPrefs.setLinkedCaretakers(linked.map { it.email to it.role })
                ApiClient.Result.Success(linked)
            }
        }
    }

    /** For when FCM is integrated; currently never called with a real token. */
    suspend fun updatePushToken(fcmToken: String): ApiClient.Result<Any?> =
        ApiClient.deviceRequest("PUT", "api/device/push-token", JSONObject().put("fcmToken", fcmToken), tokens)

    private companion object {
        const val ROLE_PRIMARY = "primary"
    }
}
