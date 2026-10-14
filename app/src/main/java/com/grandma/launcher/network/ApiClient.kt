package com.grandma.launcher.network

import com.grandma.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Lightweight REST API Client for Grandma's Launcher Backend communication.
 *
 * Successful responses use the `{ success, data }` envelope; [Result.Success.data] is the
 * unwrapped `data` (JSONObject, JSONArray or null for 204 / JSON null). Errors carry the
 * backend's stable `code` (e.g. DEVICE_NOT_FOUND) — branch on that, never on `message`.
 */
object ApiClient {

    const val DEVICE_API_PREFIX = "api/device/"

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(
            val statusCode: Int,
            val message: String,
            val code: String? = null,
            val cause: Throwable? = null,
            val details: JSONObject? = null
        ) : Result<Nothing>() {
            val isNetworkError: Boolean get() = statusCode == NETWORK_ERROR
        }
    }

    const val NETWORK_ERROR = -1
    const val INVALID_RESPONSE = -2

    @Volatile
    var baseUrl: String = BuildConfig.BACKEND_BASE_URL

    @Volatile
    var transport: HttpTransport = HttpUrlConnectionTransport()

    /** Unauthenticated call (registration / challenge / verify only). */
    suspend fun publicRequest(
        method: String,
        endpoint: String,
        body: JSONObject? = null
    ): Result<Any?> = withContext(Dispatchers.IO) {
        execute(method, endpoint, body, bearerToken = null)
    }

    /**
     * Device-authenticated call to `/api/device/...`: attaches the device token and, on a
     * 401, re-authenticates once and retries once.
     */
    suspend fun deviceRequest(
        method: String,
        endpoint: String,
        body: JSONObject? = null,
        tokens: DeviceTokenProvider,
        extraHeaders: Map<String, String> = emptyMap()
    ): Result<Any?> = withContext(Dispatchers.IO) {
        require(endpoint.startsWith(DEVICE_API_PREFIX)) { "Not a device endpoint: $endpoint" }
        withDeviceAuthRetry(tokens) { token -> execute(method, endpoint, body, token, extraHeaders) }
    }

    internal suspend fun <T> withDeviceAuthRetry(
        tokens: DeviceTokenProvider,
        send: suspend (token: String) -> Result<T>
    ): Result<T> {
        val token = when (val current = tokens.currentToken()) {
            is Result.Success -> current.data
            is Result.Error -> return current
        }
        val first = send(token)
        if (first !is Result.Error || first.statusCode != 401) return first

        val refreshed = when (val r = tokens.refreshToken(token)) {
            is Result.Success -> r.data
            is Result.Error -> return r
        }
        return send(refreshed)
    }

    private fun execute(
        method: String,
        endpoint: String,
        body: JSONObject?,
        bearerToken: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): Result<Any?> {
        val headers = mutableMapOf("Accept" to "application/json")
        if (body != null) headers["Content-Type"] = "application/json"
        if (bearerToken != null) headers["Authorization"] = "Bearer $bearerToken"
        headers.putAll(extraHeaders)

        val request = HttpRequest(
            method = method,
            url = "${baseUrl.trimEnd('/')}/$endpoint",
            headers = headers,
            body = body?.toString()
        )
        val response = try {
            transport.execute(request)
        } catch (e: Exception) {
            return Result.Error(NETWORK_ERROR, "Network connection failed", cause = e)
        }
        return parseResponse(response)
    }

    internal fun parseResponse(response: HttpResponse): Result<Any?> {
        val parsed: Any? = try {
            if (response.body.isBlank()) null else JSONTokener(response.body).nextValue()
        } catch (e: JSONException) {
            null
        }

        if (response.statusCode in 200..299) {
            val envelope = parsed as? JSONObject ?: return Result.Success(null)
            val data = envelope.opt("data")
            return Result.Success(if (data == JSONObject.NULL) null else data)
        }

        val error = parsed as? JSONObject
        return Result.Error(
            statusCode = response.statusCode,
            message = error?.optString("message")?.takeIf { it.isNotBlank() } ?: "HTTP ${response.statusCode}",
            code = error?.optString("code")?.takeIf { it.isNotBlank() },
            details = error?.optJSONObject("details")
        )
    }
}
