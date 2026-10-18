package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Copies a caretaker recording onto the phone so it can play with no network later. */
class MediaRepository(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context)
) {
    private val voiceDir = File(context.applicationContext.filesDir, "voice")

    suspend fun ensureClip(assetId: String): Boolean = withContext(Dispatchers.IO) {
        val dest = File(voiceDir, "$assetId.m4a")
        if (dest.exists() && dest.length() > 0L) return@withContext true
        if (!ASSET_ID.matches(assetId)) return@withContext false
        when (val result = ApiClient.withDeviceAuthRetry(tokens) { token -> fetch(token, assetId, dest) }) {
            is ApiClient.Result.Success -> true
            is ApiClient.Result.Error -> false
        }
    }

    private fun fetch(token: String, assetId: String, dest: File): ApiClient.Result<File> {
        val connection = (URL("${ApiClient.baseUrl.trimEnd('/')}/api/device/media/$assetId").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = connection.responseCode
            if (code == 401) return ApiClient.Result.Error(401, "Unauthorized", "UNAUTHORIZED")
            if (code !in 200..299) return ApiClient.Result.Error(code, "Could not load the recording")
            val type = connection.contentType.orEmpty()
            if (!type.startsWith("audio")) {
                return ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Recording was not audio")
            }
            voiceDir.mkdirs()
            val partial = File(voiceDir, "$assetId.m4a.part")
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) {
                            partial.delete()
                            return ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Recording is too large")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!partial.renameTo(dest)) {
                partial.copyTo(dest, overwrite = true)
                partial.delete()
            }
            return ApiClient.Result.Success(dest)
        } catch (e: Exception) {
            return ApiClient.Result.Error(ApiClient.NETWORK_ERROR, "Network connection failed", cause = e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        val ASSET_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
