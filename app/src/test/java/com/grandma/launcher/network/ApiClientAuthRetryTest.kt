package com.grandma.launcher.network

import com.grandma.launcher.testutil.FakeTransport
import com.grandma.launcher.testutil.FakeTransport.Companion.error
import com.grandma.launcher.testutil.FakeTransport.Companion.ok
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiClientAuthRetryTest {

    private lateinit var transport: FakeTransport
    private val originalTransport = ApiClient.transport

    /** Hands out dt_1, then dt_2 after a refresh; counts refreshes. */
    private class CountingTokens(private val failRefresh: Boolean = false) : DeviceTokenProvider {
        var token = "dt_1"
        var refreshes = 0
        var lastRejected: String? = null

        override suspend fun currentToken() = ApiClient.Result.Success(token)

        override suspend fun refreshToken(rejectedToken: String?): ApiClient.Result<String> {
            refreshes++
            lastRejected = rejectedToken
            if (failRefresh) return ApiClient.Result.Error(401, "verify failed", "INVALID_SIGNATURE")
            token = "dt_${refreshes + 1}"
            return ApiClient.Result.Success(token)
        }
    }

    @Before
    fun setUp() {
        transport = FakeTransport()
        ApiClient.transport = transport
    }

    @After
    fun tearDown() {
        ApiClient.transport = originalTransport
    }

    @Test
    fun attachesBearerTokenAndUnwrapsEnvelope() = runBlocking {
        transport.on("GET", "/api/device/me", 200, ok("""{"deviceId":"d1"}"""))
        val result = ApiClient.deviceRequest("GET", "api/device/me", null, CountingTokens())

        assertEquals("d1", ((result as ApiClient.Result.Success).data as JSONObject).getString("deviceId"))
        assertEquals("Bearer dt_1", transport.requests.single().headers["Authorization"])
    }

    @Test
    fun publicRequestsHaveNoAuthorizationHeader() = runBlocking {
        transport.on("POST", "/api/devices/challenge", 200, ok("""{"challenge":"ab"}"""))
        ApiClient.publicRequest("POST", "api/devices/challenge", JSONObject().put("deviceId", "d1"))
        assertNull(transport.requests.single().headers["Authorization"])
    }

    @Test
    fun on401ReauthenticatesOnceAndRetriesOnce() = runBlocking {
        transport.on(
            "POST", "/api/device/snapshot",
            { _ -> HttpResponse(401, error(401, "DEVICE_TOKEN_INVALID")) },
            { _ -> HttpResponse(201, ok("""{"id":"s1"}""")) }
        )
        val tokens = CountingTokens()
        val result = ApiClient.deviceRequest("POST", "api/device/snapshot", JSONObject(), tokens)

        assertTrue(result is ApiClient.Result.Success)
        assertEquals(1, tokens.refreshes)
        assertEquals("dt_1", tokens.lastRejected)
        val calls = transport.calls("POST", "/api/device/snapshot")
        assertEquals(2, calls.size)
        assertEquals("Bearer dt_1", calls[0].headers["Authorization"])
        assertEquals("Bearer dt_2", calls[1].headers["Authorization"])
    }

    @Test
    fun secondConsecutive401IsReturnedWithoutLooping() = runBlocking {
        transport.on("GET", "/api/device/commands", 401, error(401, "DEVICE_TOKEN_INVALID"))
        val tokens = CountingTokens()
        val result = ApiClient.deviceRequest("GET", "api/device/commands?status=pending", null, tokens)

        assertEquals(401, (result as ApiClient.Result.Error).statusCode)
        assertEquals("DEVICE_TOKEN_INVALID", result.code)
        assertEquals(1, tokens.refreshes)
        assertEquals(2, transport.calls("GET", "/api/device/commands").size)
    }

    @Test
    fun failedReauthIsReturnedWithoutRetrying() = runBlocking {
        transport.on("GET", "/api/device/me", 401, error(401, "DEVICE_TOKEN_INVALID"))
        val result = ApiClient.deviceRequest("GET", "api/device/me", null, CountingTokens(failRefresh = true))

        assertEquals("INVALID_SIGNATURE", (result as ApiClient.Result.Error).code)
        assertEquals(1, transport.calls("GET", "/api/device/me").size)
    }

    @Test
    fun non401ErrorsAreNotRetried() = runBlocking {
        transport.on("POST", "/api/device/invitations", 409, error(409, "PRIMARY_EXISTS"))
        val tokens = CountingTokens()
        val result = ApiClient.deviceRequest("POST", "api/device/invitations", JSONObject(), tokens)

        assertEquals("PRIMARY_EXISTS", (result as ApiClient.Result.Error).code)
        assertEquals(0, tokens.refreshes)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun networkFailureIsReportedAsNetworkError() = runBlocking {
        val result = ApiClient.deviceRequest("GET", "api/device/me", null, CountingTokens())
        assertTrue((result as ApiClient.Result.Error).isNetworkError)
    }

    @Test
    fun noContentResponseIsSuccessWithNullData() = runBlocking {
        transport.on("PUT", "/api/device/push-token", 204, "")
        val result = ApiClient.deviceRequest("PUT", "api/device/push-token", JSONObject(), CountingTokens())
        assertNull((result as ApiClient.Result.Success).data)
    }
}
