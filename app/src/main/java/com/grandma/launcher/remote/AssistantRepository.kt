package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import com.grandma.launcher.network.HttpRequest
import com.grandma.launcher.network.HttpTransport
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Ask on the phone. Speech stays on the device. This class sends the recognized
 * words to Gemini with a one-use token from POST /api/device/ai/session.
 * The long-lived key never reaches the phone.
 */
class AssistantRepository(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context),
    private val transport: HttpTransport = ApiClient.transport
) {

    suspend fun ask(transcript: String): ApiClient.Result<String> {
        val heard = transcript.trim().take(MAX_TRANSCRIPT)
        if (heard.isEmpty()) {
            return ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Nothing was heard", "EMPTY")
        }
        val session = when (val opened = ApiClient.deviceRequest("POST", SESSION_PATH, JSONObject(), tokens)) {
            is ApiClient.Result.Error -> return opened
            is ApiClient.Result.Success -> parseSession(opened.data as? JSONObject)
                ?: return unavailable()
        }
        val response = try {
            transport.execute(
                HttpRequest(
                    method = "POST",
                    url = session.generateContentUrl,
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Content-Type" to "application/json",
                        "x-goog-api-key" to session.token
                    ),
                    body = generateBody(heard).toString()
                )
            )
        } catch (_: Exception) {
            return ApiClient.Result.Error(ApiClient.NETWORK_ERROR, "Network connection failed")
        }
        if (response.statusCode !in 200..299) return unavailable()
        val reply = replyFrom(response.body) ?: return unavailable()
        return ApiClient.Result.Success(reply)
    }

    private fun unavailable(): ApiClient.Result.Error =
        ApiClient.Result.Error(503, "The assistant could not start.", "AI_UNAVAILABLE")

    companion object {
        const val SESSION_PATH = "api/device/ai/session"
        private const val MAX_TRANSCRIPT = 500
        private const val MAX_SPOKEN = 400
        private const val HOST = "generativelanguage.googleapis.com"

        /**
         * Spoken Hindi, short enough to hear. This text is not logged.
         */
        private const val SPOKEN_REPLY =
            "You speak to a grandmother who cannot read. Answer in simple Hindi, in one or two short spoken sentences. No markdown."

        internal fun parseSession(data: JSONObject?): AssistantSession? {
            if (data == null) return null
            val token = data.optString("token").trim()
            val url = data.optString("generateContentUrl").trim()
            if (token.isEmpty() || token.length > 512 || token.any { it.isWhitespace() }) return null
            if (!isGenerateContentUrl(url)) return null
            val model = data.optString("model").trim()
            val expires = data.optInt("expiresInSec", 60).coerceAtLeast(1)
            return AssistantSession(model, token, url, expires)
        }

        internal fun isGenerateContentUrl(url: String): Boolean {
            if (url.length > 300) return false
            val uri = try {
                URI(url)
            } catch (_: Exception) {
                return false
            }
            if (uri.scheme != "https" || uri.userInfo != null) return false
            if (!uri.host.equals(HOST, ignoreCase = true)) return false
            if (!uri.rawQuery.isNullOrEmpty() || !uri.rawFragment.isNullOrEmpty()) return false
            val path = uri.path ?: return false
            return path.startsWith("/v1beta/models/") &&
                path.endsWith(":generateContent") &&
                !path.contains("..")
        }

        internal fun generateBody(transcript: String): JSONObject {
            val heard = transcript.trim().take(MAX_TRANSCRIPT)
            return JSONObject()
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SPOKEN_REPLY)))
                )
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", heard)))
                    )
                )
                .put(
                    "generationConfig",
                    JSONObject().put("maxOutputTokens", 120).put("temperature", 0.4)
                )
        }

        internal fun replyFrom(body: String): String? {
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                return null
            }
            val parts = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return null
            val text = buildString {
                for (index in 0 until parts.length()) {
                    val piece = parts.optJSONObject(index)?.optString("text").orEmpty().trim()
                    if (piece.isEmpty()) continue
                    if (isNotEmpty()) append(' ')
                    append(piece)
                }
            }
            return spokenReply(text).takeIf { it.isNotEmpty() }
        }

        internal fun spokenReply(text: String, limit: Int = MAX_SPOKEN): String {
            val trimmed = text.trim().replace(Regex("\\s+"), " ")
            if (trimmed.length <= limit) return trimmed
            val cut = trimmed.substring(0, limit)
            val sentence = cut.lastIndexOfAny(charArrayOf('।', '.', '?', '!'))
            val end = if (sentence >= 40) sentence + 1 else limit
            return cut.substring(0, end).trim()
        }
    }
}

internal data class AssistantSession(
    val model: String,
    val token: String,
    val generateContentUrl: String,
    val expiresInSec: Int
)
