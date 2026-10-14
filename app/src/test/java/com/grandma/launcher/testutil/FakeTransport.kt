package com.grandma.launcher.testutil

import com.grandma.launcher.network.HttpRequest
import com.grandma.launcher.network.HttpResponse
import com.grandma.launcher.network.HttpTransport
import java.io.IOException

/**
 * Scripted transport: responses are queued per "METHOD path" (path without base URL or
 * query) and consumed in order; the last one repeats. Unscripted routes throw IOException.
 */
class FakeTransport : HttpTransport {

    val requests = mutableListOf<HttpRequest>()
    private val routes = mutableMapOf<String, ArrayDeque<(HttpRequest) -> HttpResponse>>()

    fun on(method: String, path: String, vararg responses: (HttpRequest) -> HttpResponse): FakeTransport {
        routes.getOrPut("$method $path") { ArrayDeque() }.addAll(responses)
        return this
    }

    fun on(method: String, path: String, status: Int, body: String): FakeTransport =
        on(method, path, { _ -> HttpResponse(status, body) })

    fun calls(method: String, path: String): List<HttpRequest> =
        requests.filter { it.method == method && pathOf(it) == path }

    override fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val queue = routes["${request.method} ${pathOf(request)}"]
            ?: throw IOException("No route for ${request.method} ${pathOf(request)}")
        val responder = if (queue.size > 1) queue.removeFirst() else queue.first()
        return responder(request)
    }

    private fun pathOf(request: HttpRequest): String =
        "/" + request.url.substringAfter("://").substringAfter('/').substringBefore('?')

    companion object {
        fun ok(data: String) = """{"success":true,"data":$data}"""
        fun error(status: Int, code: String) =
            """{"statusCode":$status,"error":"Err","message":"m","code":"$code"}"""
    }
}
