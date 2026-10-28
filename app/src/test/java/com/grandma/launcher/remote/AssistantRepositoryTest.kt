package com.grandma.launcher.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import com.grandma.launcher.testutil.FakeTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssistantRepositoryTest {

    private lateinit var transport: FakeTransport
    private val originalTransport = ApiClient.transport
    private val originalBase = ApiClient.baseUrl
    private val tokens = object : DeviceTokenProvider {
        override suspend fun currentToken() = ApiClient.Result.Success("dt_test")
        override suspend fun refreshToken(rejectedToken: String?) = ApiClient.Result.Success("dt_test")
    }

    @Before
    fun setUp() {
        transport = FakeTransport()
        ApiClient.transport = transport
        ApiClient.baseUrl = "http://localhost:3000/"
    }

    @After
    fun tearDown() {
        ApiClient.transport = originalTransport
        ApiClient.baseUrl = originalBase
    }

    @Test
    fun sendsRecognizedWordsWithTheSessionTokenAndNotTheDeviceToken() = runBlocking {
        scriptSession()
        transport.on(
            "POST",
            "/v1beta/models/gemini-3.8-flash:generateContent",
            200,
            """{"candidates":[{"content":{"parts":[{"text":"Pehla."},{"text":"Doosra."}]}}]}"""
        )
        val result = repository().ask("  Aaj mausam  ")

        assertEquals("Pehla. Doosra.", (result as ApiClient.Result.Success).data)
        val session = transport.calls("POST", "/api/device/ai/session").single()
        assertEquals("Bearer dt_test", session.headers["Authorization"])
        val google = transport.calls("POST", "/v1beta/models/gemini-3.8-flash:generateContent").single()
        assertEquals("auth_tokens/test-token", google.headers["x-goog-api-key"])
        assertNull(google.headers["Authorization"])
        val raw = google.body ?: error("missing body")
        val body = JSONObject(raw)
        val heard = body.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("Aaj mausam", heard)
        assertTrue(body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text").isNotBlank())
        assertFalse(raw.contains("auth_tokens/test-token"))
        assertFalse(raw.contains("dt_test"))
        assertFalse(raw.contains("AIza"))
    }

    @Test
    fun staysQuietWhenTheServerHasNoGeminiKey() = runBlocking {
        transport.on("POST", "/api/device/ai/session", 503, FakeTransport.error(503, "AI_NOT_CONFIGURED"))
        val result = repository().ask("madad")
        assertTrue(result is ApiClient.Result.Error)
        assertEquals("AI_NOT_CONFIGURED", (result as ApiClient.Result.Error).code)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun doesNotSendTheTokenToAnyOtherHost() = runBlocking {
        scriptSession(url = "https://evil.example/v1beta/models/gemini-3.8-flash:generateContent")
        val result = repository().ask("madad")
        assertEquals("AI_UNAVAILABLE", (result as ApiClient.Result.Error).code)
        assertEquals(1, transport.requests.size)
        assertFalse(result.message.contains("auth_tokens"))
    }

    @Test
    fun missingTokenDoesNotCallGemini() = runBlocking {
        transport.on(
            "POST",
            "/api/device/ai/session",
            200,
            FakeTransport.ok("""{"mode":"text","model":"gemini-3.8-flash","token":"","generateContentUrl":"$GOOD_URL"}""")
        )
        val result = repository().ask("madad")
        assertEquals("AI_UNAVAILABLE", (result as ApiClient.Result.Error).code)
        assertTrue(transport.calls("POST", "/v1beta/models/gemini-3.8-flash:generateContent").isEmpty())
    }

    @Test
    fun geminiFailureDoesNotExposeTheResponseBody() = runBlocking {
        scriptSession()
        transport.on(
            "POST",
            "/v1beta/models/gemini-3.8-flash:generateContent",
            400,
            """{"error":{"message":"auth_tokens/test-token leaked"}}"""
        )
        val result = repository().ask("madad") as ApiClient.Result.Error
        assertEquals("AI_UNAVAILABLE", result.code)
        assertFalse(result.message.contains("auth_tokens"))
        assertFalse(result.message.contains("leaked"))
    }

    @Test
    fun blankSpeechDoesNotCallTheNetwork() = runBlocking {
        val result = repository().ask("   ")
        assertEquals("EMPTY", (result as ApiClient.Result.Error).code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun longReplyIsShortenedOnASentence() {
        val sentence = "Aaj bahut dhoop hai aur mausam bilkul saaf hai."
        val text = sentence + " " + "A".repeat(500)
        val spoken = AssistantRepository.replyFrom(
            """{"candidates":[{"content":{"parts":[{"text":${JSONObject.quote(text)}}]}}]}"""
        )
        assertEquals(sentence, spoken)
    }

    @Test
    fun savingAClipKeepsTheCaretakerModel() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.filesDir, "device-config.json")
        file.writeText("""{"voice":{"clips":[],"ttsRate":0.9,"model":"gemini-3.8-flash"}}""")
        DeviceConfigRepository(context).saveClip("ask", "asset-1")
        val voice = JSONObject(file.readText()).getJSONObject("voice")
        assertEquals("gemini-3.8-flash", voice.getString("model"))
        assertEquals("asset-1", voice.getJSONArray("clips").getJSONObject(0).getString("assetId"))
    }

    private fun repository() = AssistantRepository(
        ApplicationProvider.getApplicationContext(),
        tokens,
        transport
    )

    private fun scriptSession(url: String = GOOD_URL) {
        transport.on(
            "POST",
            "/api/device/ai/session",
            200,
            FakeTransport.ok(
                """{"mode":"text","model":"gemini-3.8-flash","token":"auth_tokens/test-token","expiresInSec":60,"generateContentUrl":"$url"}"""
            )
        )
    }

    private companion object {
        const val GOOD_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
    }
}
