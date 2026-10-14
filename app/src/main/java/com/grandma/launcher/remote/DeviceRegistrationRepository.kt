package com.grandma.launcher.remote

import android.content.Context
import android.os.Build
import android.util.Log
import com.grandma.launcher.BuildConfig
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.AndroidKeystoreDeviceKey
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceSecurityManager
import com.grandma.launcher.network.DeviceTokenProvider
import com.grandma.launcher.network.DeviceTokenStore
import com.grandma.launcher.network.KeystoreAesGcmCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

/**
 * Device registration and challenge-response verification; owns the device token.
 *
 * register (or challenge) → sign challenge → verify-signature → `dt_…` token.
 * Nothing is cached as "verified forever": an unknown-device answer from the backend
 * re-registers, and a key mismatch moves this phone to a brand-new deviceId.
 */
class DeviceRegistrationRepository internal constructor(
    private val appPrefs: AppPreferences,
    private val security: DeviceSecurityManager,
    private val tokenStore: DeviceTokenStore,
    private val metadataProvider: () -> JSONObject
) : DeviceTokenProvider {

    constructor(context: Context) : this(AppPreferences(context.applicationContext))

    private constructor(appPrefs: AppPreferences) : this(
        appPrefs,
        DeviceSecurityManager(AndroidKeystoreDeviceKey()),
        DeviceTokenStore(appPrefs, KeystoreAesGcmCipher()),
        ::defaultMetadata
    )

    override suspend fun currentToken(): ApiClient.Result<String> = withContext(Dispatchers.IO) {
        tokenStore.validToken()?.let { return@withContext ApiClient.Result.Success(it) }
        refreshToken(null)
    }

    override suspend fun refreshToken(rejectedToken: String?): ApiClient.Result<String> =
        withContext(Dispatchers.IO) {
            authMutex.withLock {
                val stored = tokenStore.validToken()
                if (stored != null && stored != rejectedToken) {
                    ApiClient.Result.Success(stored)
                } else {
                    authenticateLocked()
                }
            }
        }

    private suspend fun authenticateLocked(): ApiClient.Result<String> {
        tokenStore.clear()
        var attempt = 0
        while (true) {
            attempt++
            val challenge = when (val c = obtainChallenge()) {
                is ApiClient.Result.Success -> c.data
                is ApiClient.Result.Error -> return c
            }
            val verifyBody = JSONObject()
                .put("deviceId", appPrefs.deviceId)
                .put("challenge", challenge)
                .put("signature", security.signChallenge(challenge))

            when (val verify = ApiClient.publicRequest("POST", "api/devices/verify-signature", verifyBody)) {
                is ApiClient.Result.Success -> {
                    val data = verify.data as? JSONObject
                    val token = data?.optString("deviceToken").orEmpty()
                    if (token.isBlank()) {
                        return ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "verify-signature returned no deviceToken")
                    }
                    val expiresAt = parseIsoMillis(data?.optString("expiresAt"))
                        ?: (System.currentTimeMillis() + FALLBACK_TOKEN_TTL_MS)
                    tokenStore.save(token, expiresAt)
                    return ApiClient.Result.Success(token)
                }
                is ApiClient.Result.Error -> {
                    if (attempt >= MAX_AUTH_ATTEMPTS || verify.code !in RETRYABLE_VERIFY_CODES) return verify
                    if (verify.code == CODE_DEVICE_NOT_FOUND) appPrefs.isRegisteredWithBackend = false
                }
            }
        }
    }

    private suspend fun obtainChallenge(): ApiClient.Result<String> {
        if (appPrefs.isRegisteredWithBackend) {
            val body = JSONObject().put("deviceId", appPrefs.deviceId)
            when (val result = ApiClient.publicRequest("POST", "api/devices/challenge", body)) {
                is ApiClient.Result.Success -> return challengeFrom(result.data)
                is ApiClient.Result.Error -> {
                    if (result.code != CODE_DEVICE_NOT_FOUND) return result
                    appPrefs.isRegisteredWithBackend = false
                }
            }
        }
        return register()
    }

    private suspend fun register(): ApiClient.Result<String> {
        var result = postRegister()
        if (result is ApiClient.Result.Error && result.code == CODE_DEVICE_KEY_MISMATCH) {
            resetIdentity()
            result = postRegister()
        }
        return when (result) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> {
                appPrefs.isRegisteredWithBackend = true
                challengeFrom(result.data)
            }
        }
    }

    private suspend fun postRegister(): ApiClient.Result<Any?> {
        val body = JSONObject()
            .put("deviceId", appPrefs.deviceId)
            .put("publicKey", security.publicKeyPem())
            .put("keyAlgorithm", DeviceSecurityManager.KEY_ALGORITHM)
            .put("deviceMetadata", metadataProvider())
        return ApiClient.publicRequest("POST", "api/devices/register", body)
    }

    /** The backend holds a different key for our deviceId; it will never accept ours. */
    private fun resetIdentity() {
        Log.e(TAG, "Backend has a different key for this device; switching to a new device id")
        appPrefs.rotateDeviceId()
        appPrefs.clearPairingState()
        tokenStore.clear()
    }

    private fun challengeFrom(data: Any?): ApiClient.Result<String> {
        val challenge = (data as? JSONObject)?.optString("challenge").orEmpty()
        return if (challenge.isBlank()) {
            ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Server returned empty challenge")
        } else {
            ApiClient.Result.Success(challenge)
        }
    }

    companion object {
        private const val TAG = "DeviceRegistration"
        private const val MAX_AUTH_ATTEMPTS = 2
        private const val FALLBACK_TOKEN_TTL_MS = 60 * 60 * 1000L

        const val CODE_DEVICE_NOT_FOUND = "DEVICE_NOT_FOUND"
        const val CODE_DEVICE_KEY_MISMATCH = "DEVICE_KEY_MISMATCH"
        private val RETRYABLE_VERIFY_CODES = setOf(
            "CHALLENGE_INVALID", "CHALLENGE_EXPIRED", "CHALLENGE_USED", CODE_DEVICE_NOT_FOUND
        )

        private val authMutex = Mutex()

        internal fun parseIsoMillis(value: String?): Long? = try {
            if (value.isNullOrBlank()) null else Instant.parse(value).toEpochMilli()
        } catch (e: Exception) {
            null
        }

        private fun defaultMetadata(): JSONObject = JSONObject()
            .put("model", Build.MODEL)
            .put("manufacturer", Build.MANUFACTURER)
            .put("osVersion", Build.VERSION.RELEASE)
            .put("appVersion", BuildConfig.VERSION_NAME)
    }
}
