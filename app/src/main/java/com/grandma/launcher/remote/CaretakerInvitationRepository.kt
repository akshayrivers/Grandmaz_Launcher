package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import org.json.JSONObject

/**
 * Primary-caretaker invitation created from grandma's phone during setup. The backend
 * emails the magic link; this phone only shows the 6-digit pairing code.
 */
class CaretakerInvitationRepository(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context)
) {

    data class Invitation(val invitationId: String, val pairingCode: String, val expiresAtMs: Long)

    data class InvitationStatus(val status: String, val expiresAtMs: Long?)

    suspend fun createInvitation(email: String): ApiClient.Result<Invitation> {
        val body = JSONObject().put("email", email)
        return when (val result = ApiClient.deviceRequest("POST", "api/device/invitations", body, tokens)) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> {
                val data = result.data as? JSONObject
                val id = data?.optString("invitationId").orEmpty()
                val code = data?.optString("pairingCode").orEmpty()
                if (id.isBlank() || code.isBlank()) {
                    ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Invitation response incomplete")
                } else {
                    val expiresAt = DeviceRegistrationRepository.parseIsoMillis(data?.optString("expiresAt"))
                        ?: (System.currentTimeMillis() + DEFAULT_TTL_MS)
                    ApiClient.Result.Success(Invitation(id, code, expiresAt))
                }
            }
        }
    }

    suspend fun getInvitationStatus(invitationId: String): ApiClient.Result<InvitationStatus> {
        return when (val result = ApiClient.deviceRequest("GET", "api/device/invitations/$invitationId", null, tokens)) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> {
                val data = result.data as? JSONObject
                val status = data?.optString("status").orEmpty()
                if (status.isBlank()) {
                    ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Invitation status missing")
                } else {
                    ApiClient.Result.Success(
                        InvitationStatus(status, DeviceRegistrationRepository.parseIsoMillis(data?.optString("expiresAt")))
                    )
                }
            }
        }
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_EXPIRED = "expired"
        const val STATUS_REVOKED = "revoked"
        const val STATUS_LOCKED = "locked"

        const val CODE_PRIMARY_EXISTS = "PRIMARY_EXISTS"
        const val CODE_INVITATION_NOT_FOUND = "INVITATION_NOT_FOUND"

        private const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
