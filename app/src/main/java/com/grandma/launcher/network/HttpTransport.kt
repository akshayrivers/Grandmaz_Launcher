package com.grandma.launcher.network

import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?
)

data class HttpResponse(val statusCode: Int, val body: String)

/**
 * Blocking HTTP exchange. Swappable so [ApiClient] can be unit-tested without a server.
 * Throws [IOException] on connection failure; non-2xx statuses are returned, not thrown.
 */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun execute(request: HttpRequest): HttpResponse
}

class HttpUrlConnectionTransport : HttpTransport {

    override fun execute(request: HttpRequest): HttpResponse {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            request.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (request.body != null) doOutput = true
        }
        try {
            if (request.body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(request.body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
