package com.grandma.launcher.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceSecurityManager
import com.grandma.launcher.network.DeviceTokenStore
import com.grandma.launcher.network.HttpRequest
import com.grandma.launcher.network.HttpResponse
import com.grandma.launcher.testutil.FakeTransport
import com.grandma.launcher.testutil.FakeTransport.Companion.error
import com.grandma.launcher.testutil.FakeTransport.Companion.ok
import com.grandma.launcher.testutil.SoftwareAesGcmCipher
import com.grandma.launcher.testutil.SoftwareDeviceKey
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceRegistrationRepositoryTest {

    private val challenge = "0f".repeat(32)
    private val originalTransport = ApiClient.transport
    private lateinit var transport: FakeTransport
    private lateinit var context: Context
    private lateinit var prefs: AppPreferences
    private lateinit var tokenStore: DeviceTokenStore
    private lateinit var repo: DeviceRegistrationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("grandma_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = AppPreferences(context)
        tokenStore = DeviceTokenStore(prefs, SoftwareAesGcmCipher())
        repo = DeviceRegistrationRepository(
            prefs,
            DeviceSecurityManager(SoftwareDeviceKey()),
            tokenStore,
            { JSONObject().put("model", "test") }
        )
        transport = FakeTransport()
        ApiClient.transport = transport
    }

    @After
    fun tearDown() {
        ApiClient.transport = originalTransport
    }

    private val registered = ok("""{"deviceId":"x","isVerified":false,"keyAlgorithm":"EC_P256","challenge":"$challenge","challengeExpiresAt":"2030-01-01T00:00:00Z"}""")
    private val verified = ok("""{"deviceId":"x","deviceToken":"dt_abc","expiresAt":"2099-01-01T00:00:00.000Z"}""")

    private fun body(request: HttpRequest) = JSONObject(request.body!!)

    @Test
    fun firstRunRegistersWithEcKeyAndStoresEncryptedToken() = runBlocking {
        transport.on("POST", "/api/devices/register", 201, registered)
            .on("POST", "/api/devices/verify-signature", 200, verified)

        val result = repo.currentToken()

        assertEquals("dt_abc", (result as ApiClient.Result.Success).data)
        val register = body(transport.calls("POST", "/api/devices/register").single())
        assertEquals(prefs.deviceId, register.getString("deviceId"))
        assertEquals("EC_P256", register.getString("keyAlgorithm"))
        assertTrue(register.getString("publicKey").startsWith("-----BEGIN PUBLIC KEY-----\n"))

        // The signature must verify against the registered SPKI key, as the backend does.
        val verify = body(transport.calls("POST", "/api/devices/verify-signature").single())
        assertEquals(challenge, verify.getString("challenge"))
        val spki = register.getString("publicKey").lines().filterNot { it.startsWith("-----") }.joinToString("")
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(spki)))
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey)
            update(challenge.toByteArray(Charsets.UTF_8))
        }
        assertTrue(verifier.verify(Base64.getDecoder().decode(verify.getString("signature"))))

        assertTrue(prefs.isRegisteredWithBackend)
        assertFalse(prefs.deviceTokenCiphertext.contains("dt_abc"))
        assertEquals("dt_abc", tokenStore.validToken())
    }

    @Test
    fun storedTokenIsReusedWithoutNetwork() = runBlocking {
        tokenStore.save("dt_cached", System.currentTimeMillis() + 3_600_000)
        assertEquals("dt_cached", (repo.currentToken() as ApiClient.Result.Success).data)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun expiredTokenTriggersChallengeForRegisteredDevice() = runBlocking {
        prefs.isRegisteredWithBackend = true
        tokenStore.save("dt_old", System.currentTimeMillis() - 1)
        transport.on("POST", "/api/devices/challenge", 200, ok("""{"challenge":"$challenge","expiresAt":"2030-01-01T00:00:00Z"}"""))
            .on("POST", "/api/devices/verify-signature", 200, verified)

        assertEquals("dt_abc", (repo.currentToken() as ApiClient.Result.Success).data)
        assertTrue(transport.calls("POST", "/api/devices/register").isEmpty())
    }

    @Test
    fun unknownDeviceOnChallengeReRegisters() = runBlocking {
        prefs.isRegisteredWithBackend = true
        transport.on("POST", "/api/devices/challenge", 404, error(404, "DEVICE_NOT_FOUND"))
            .on("POST", "/api/devices/register", 201, registered)
            .on("POST", "/api/devices/verify-signature", 200, verified)

        assertTrue(repo.currentToken() is ApiClient.Result.Success)
        assertEquals(1, transport.calls("POST", "/api/devices/register").size)
        assertTrue(prefs.isRegisteredWithBackend)
    }

    @Test
    fun keyMismatchMovesToNewDeviceIdAndClearsPairing() = runBlocking {
        val oldId = prefs.deviceId
        prefs.isPaired = true
        prefs.primaryCaretakerPhone = "+910000000000"
        transport.on(
            "POST", "/api/devices/register",
            { _ -> HttpResponse(409, error(409, "DEVICE_KEY_MISMATCH")) },
            { _ -> HttpResponse(201, registered) }
        ).on("POST", "/api/devices/verify-signature", 200, verified)

        assertTrue(repo.currentToken() is ApiClient.Result.Success)

        val ids = transport.calls("POST", "/api/devices/register").map { body(it).getString("deviceId") }
        assertEquals(oldId, ids[0])
        assertNotEquals(oldId, ids[1])
        assertEquals(ids[1], prefs.deviceId)
        assertFalse(prefs.isPaired)
        assertEquals("", prefs.primaryCaretakerPhone)
    }

    @Test
    fun expiredChallengeIsRetriedOnceWithAFreshChallenge() = runBlocking {
        transport.on("POST", "/api/devices/register", 201, registered)
            .on("POST", "/api/devices/challenge", 200, ok("""{"challenge":"$challenge"}"""))
            .on(
                "POST", "/api/devices/verify-signature",
                { _ -> HttpResponse(401, error(401, "CHALLENGE_EXPIRED")) },
                { _ -> HttpResponse(200, verified) }
            )

        assertTrue(repo.currentToken() is ApiClient.Result.Success)
        assertEquals(2, transport.calls("POST", "/api/devices/verify-signature").size)
    }

    @Test
    fun invalidSignatureIsNotRetried() = runBlocking {
        transport.on("POST", "/api/devices/register", 201, registered)
            .on("POST", "/api/devices/verify-signature", 401, error(401, "INVALID_SIGNATURE"))

        val result = repo.currentToken()
        assertEquals("INVALID_SIGNATURE", (result as ApiClient.Result.Error).code)
        assertEquals(1, transport.calls("POST", "/api/devices/verify-signature").size)
    }

    @Test
    fun refreshReturnsNewerTokenWithoutReauthenticating() = runBlocking {
        tokenStore.save("dt_newer", System.currentTimeMillis() + 3_600_000)
        val result = repo.refreshToken(rejectedToken = "dt_older")
        assertEquals("dt_newer", (result as ApiClient.Result.Success).data)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun legacySecurityStateIsWipedAndDeviceIdRotated() {
        val raw = context.getSharedPreferences("grandma_app_prefs", Context.MODE_PRIVATE)
        raw.edit()
            .clear()
            .putString("device_id", "device_legacy")
            .putString("device_private_key", "-----BEGIN PRIVATE KEY-----x")
            .putString("device_public_key", "-----BEGIN PUBLIC KEY-----x")
            .putBoolean("is_device_verified", true)
            .putString("backend_base_url", "http://1.2.3.4:3000")
            .commit()

        prefs.migrateLegacySecurityState()

        assertFalse(raw.contains("device_private_key"))
        assertFalse(raw.contains("device_public_key"))
        assertFalse(raw.contains("is_device_verified"))
        assertFalse(raw.contains("backend_base_url"))
        assertNotEquals("device_legacy", prefs.deviceId)
        val rotated = prefs.deviceId
        prefs.migrateLegacySecurityState() // idempotent
        assertEquals(rotated, prefs.deviceId)
    }
}
