package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import org.json.JSONObject

/**
 * Handles posting Help Requests (general, sos, medical, tech_support) to the backend.
 */
class HelpRequestRepository(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context)
) {

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
    ): ApiClient.Result<Any?> {
        val body = JSONObject().apply {
            put("title", title)
            if (!description.isNullOrBlank()) {
                put("description", description)
            }
            put("type", type.value)
        }
        return ApiClient.deviceRequest("POST", "api/device/help-requests", body, tokens)
    }
}
