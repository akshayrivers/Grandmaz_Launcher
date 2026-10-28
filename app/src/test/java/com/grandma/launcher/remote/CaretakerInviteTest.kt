package com.grandma.launcher.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import com.grandma.launcher.testutil.FakeTransport
import com.grandma.launcher.testutil.FakeTransport.Companion.ok
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaretakerInviteTest {

    private val originalTransport = ApiClient.transport
    private lateinit var transport: FakeTransport
    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    private val tokens = object : DeviceTokenProvider {
        override suspend fun currentToken() = ApiClient.Result.Success("dt_test")
        override suspend fun refreshToken(rejectedToken: String?) = currentToken()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("grandma_app_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = AppPreferences(context)
        transport = FakeTransport()
        ApiClient.transport = transport
    }

    @After
    fun tearDown() {
        ApiClient.transport = originalTransport
    }

    @Test
    fun invitationKeepsThePairingCodeAndAcceptPath() = runBlocking {
        transport.on(
            "POST",
            "/api/device/invitations",
            200,
            ok(
                """{"invitationId":"inv1","email":"sister@family.com","role":"caretaker","pairingCode":"123456","expiresAt":"2030-01-01T00:00:00Z","acceptPath":"/accept-invitation?token=abc"}"""
            )
        )
        val result = CaretakerInvitationRepository(context, tokens).createInvitation("sister@family.com")
        val invite = (result as ApiClient.Result.Success).data
        assertEquals("123456", invite.pairingCode)
        assertEquals("caretaker", invite.role)
        assertEquals("/accept-invitation?token=abc", invite.acceptPath)
    }

    @Test
    fun profileKeepsEveryCaretakerForTheOfflineList() = runBlocking {
        transport.on(
            "GET",
            "/api/device/me",
            200,
            ok(
                """{"deviceId":"d1","isVerified":true,"displayName":"Dadi","caretakers":[
                    {"caretakerId":"c1","email":"local-caretaker@localhost","name":"Local","role":"primary","phoneE164":"+911234567890"},
                    {"caretakerId":"c2","email":"sister@family.com","name":"Sister","role":"caretaker","phoneE164":null}
                ]}"""
            )
        )
        val result = DeviceProfileRepository(context, tokens).linkedCaretakers()
        val people = (result as ApiClient.Result.Success).data
        assertEquals(listOf("local-caretaker@localhost", "sister@family.com"), people.map { it.email })
        assertEquals("primary", people.first().role)
        assertEquals(people.map { it.email to it.role }, prefs.linkedCaretakers())
        assertTrue(prefs.isPaired)
        assertEquals("+911234567890", prefs.primaryCaretakerPhone)
    }
}
