package com.grandma.launcher.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
<<<<<<< Updated upstream
import java.io.BufferedReader
import java.io.InputStreamReader
=======
>>>>>>> Stashed changes
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight REST API Client for Grandma's Launcher Backend communication.
 */
object ApiClient {

    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 15000

    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val statusCode: Int, val message: String, val cause: Throwable? = null) : Result<Nothing>()
    }

    suspend fun postJson(
        baseUrl: String,
        endpoint: String,
        bodyJson: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        requestJson(baseUrl, endpoint, "POST", bodyJson.toString())
    }

    suspend fun patchJson(
        baseUrl: String,
        endpoint: String,
        bodyJson: JSONObject
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        requestJson(baseUrl, endpoint, "PATCH", bodyJson.toString())
    }

    suspend fun getJson(
        baseUrl: String,
        endpoint: String
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        requestJson(baseUrl, endpoint, "GET", null)
    }

    suspend fun getJsonArray(
        baseUrl: String,
        endpoint: String
    ): Result<JSONArray> = withContext(Dispatchers.IO) {
        val fullUrl = "${baseUrl.trimEnd('/')}/$endpoint"
        try {
            val url = URL(fullUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                Result.Success(JSONArray(responseText))
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.Error(responseCode, errorText)
            }
        } catch (e: Exception) {
            Result.Error(-1, e.localizedMessage ?: "Network error", e)
        }
    }

    private fun requestJson(
        baseUrl: String,
        endpoint: String,
        method: String,
        jsonBody: String?
    ): Result<JSONObject> {
        val fullUrl = "${baseUrl.trimEnd('/')}/$endpoint"
        try {
            val url = URL(fullUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (jsonBody != null) {
                    doOutput = true
                }
            }

            if (jsonBody != null) {
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResult = if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
                return Result.Success(jsonResult)
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                return Result.Error(responseCode, errorText)
            }
        } catch (e: Exception) {
            return Result.Error(-1, e.localizedMessage ?: "Network connection failed", e)
        }
    }
}
