package com.grandma.launcher.remote

import android.content.Context
import android.os.Build
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceSecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Handles device registration and challenge-response signature verification with backend.
 */
class DeviceRegistrationRepository(private val context: Context) {

    private val appPrefs = AppPreferences(context)

    companion object {
        private val registrationMutex = Mutex()
    }

    suspend fun registerAndVerifyDevice(forceReverify: Boolean = false): ApiClient.Result<Boolean> = withContext(Dispatchers.IO) {
        registrationMutex.withLock {
            if (appPrefs.isDeviceVerified && !forceReverify) {
                return@withContext ApiClient.Result.Success(true)
            }

            val baseUrl = appPrefs.backendBaseUrl
            val deviceId = appPrefs.deviceId

        // 1. Ensure RSA KeyPair exists
        DeviceSecurityManager.ensureKeyPair(appPrefs)
        val publicKeyPem = appPrefs.devicePublicKeyPem

        // Build metadata
        val metadata = JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("release", Build.VERSION.RELEASE)
            put("appVersion", "1.0")
        }

        // Step 1: POST /api/devices/register
        val registerBody = JSONObject().apply {
            put("deviceId", deviceId)
            put("publicKey", publicKeyPem)
            put("deviceMetadata", metadata)
        }

        val regResult = ApiClient.postJson(baseUrl, "api/devices/register", registerBody)
        if (regResult is ApiClient.Result.Error) {
            appPrefs.isDeviceVerified = false
            return@withContext ApiClient.Result.Error(
                regResult.statusCode,
                "Device registration failed: ${regResult.message}"
            )
        }

        val regData = (regResult as ApiClient.Result.Success).data
        var challengeStr = regData.optString("challenge", "")

        // Fallback: POST /api/devices/challenge if not in register response
        if (challengeStr.isBlank()) {
            val challengeBody = JSONObject().apply {
                put("deviceId", deviceId)
            }
            val challengeResult = ApiClient.postJson(baseUrl, "api/devices/challenge", challengeBody)
            if (challengeResult is ApiClient.Result.Success) {
                challengeStr = challengeResult.data.optString("challenge", "")
            }
        }

        if (challengeStr.isBlank()) {
            appPrefs.isDeviceVerified = false
            return@withContext ApiClient.Result.Error(-1, "Server returned empty challenge")
        }

        // Step 3: Sign challenge using private key
        val signature = DeviceSecurityManager.signChallenge(challengeStr, appPrefs)

        // Step 4: POST /api/devices/verify-signature
        val verifyBody = JSONObject().apply {
            put("deviceId", deviceId)
            put("challenge", challengeStr)
            put("signature", signature)
        }

        val verifyResult = ApiClient.postJson(baseUrl, "api/devices/verify-signature", verifyBody)
        when (verifyResult) {
            is ApiClient.Result.Success -> {
                val isVerified = verifyResult.data.optBoolean("is_verified", true)
                appPrefs.isDeviceVerified = isVerified
                ApiClient.Result.Success(isVerified)
            }
            is ApiClient.Result.Error -> {
                appPrefs.isDeviceVerified = false
                ApiClient.Result.Error(verifyResult.statusCode, "Signature verification failed: ${verifyResult.message}")
            }
        }
        }
    }
}
