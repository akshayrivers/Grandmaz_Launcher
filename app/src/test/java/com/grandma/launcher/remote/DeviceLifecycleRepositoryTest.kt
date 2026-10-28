package com.grandma.launcher.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class DeviceLifecycleRepositoryTest {

    private val originalTransport = ApiClient.transport
    private lateinit var transport: FakeTransport
    private lateinit var context: Context

    private val tokens = object : DeviceTokenProvider {
        override suspend fun currentToken() = ApiClient.Result.Success("dt_test")
        override suspend fun refreshToken(rejectedToken: String?) = currentToken()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        transport = FakeTransport()
        ApiClient.transport = transport
    }

    @After
    fun tearDown() {
        ApiClient.transport = originalTransport
    }

    @Test
    fun uninstallTellsTheBackendBeforeTheSystemScreen() = runBlocking {
        transport.on("POST", "/api/device/uninstall", 200, ok("""{"status":"uninstalled"}"""))
        val result = DeviceLifecycleRepository(context, tokens).reportUninstalled()
        assertTrue(result is ApiClient.Result.Success)
        assertEquals(1, transport.calls("POST", "/api/device/uninstall").size)
    }

    @Test
    fun installedClearsTheUninstallNotice() = runBlocking {
        transport.on("POST", "/api/device/installed", 200, ok("""{"status":"active"}"""))
        val result = DeviceLifecycleRepository(context, tokens).reportStillInstalled()
        assertTrue(result is ApiClient.Result.Success)
        assertEquals(1, transport.calls("POST", "/api/device/installed").size)
    }
}
