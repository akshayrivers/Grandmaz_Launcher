package com.grandma.launcher.remote

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceCommandTest {

    private fun cmd(type: String, payload: String = "{}") =
        DeviceCommand.parse(JSONObject("""{"id":"c1","type":"$type","payload":$payload,"status":"pending"}"""))

    @Test
    fun parsesEveryContractType() {
        assertEquals(DeviceCommand.RingDevice("c1"), cmd("ring_device"))
        assertEquals(DeviceCommand.SyncNow("c1"), cmd("sync_now"))
        assertEquals(DeviceCommand.ReportLocation("c1"), cmd("report_location"))
        assertEquals(DeviceCommand.ShowMessage("c1", "Dawai le lo", null), cmd("show_message", """{"text":"Dawai le lo"}"""))
        assertEquals(
            DeviceCommand.ShowMessage("c1", "hi", "clip-9"),
            cmd("show_message", """{"text":"hi","voiceClipId":"clip-9"}""")
        )
        assertEquals(DeviceCommand.SetVolume("c1", 70), cmd("set_volume", """{"level":70}"""))
        assertEquals(DeviceCommand.SetVolume("c1", 0), cmd("set_volume", """{"level":0}"""))
        assertEquals(
            DeviceCommand.UpdateEmergencyNumber("c1", "+919876543210"),
            cmd("update_emergency_number", """{"e164":"+919876543210"}""")
        )
    }

    @Test
    fun missingPayloadIsTreatedAsEmpty() {
        assertEquals(DeviceCommand.RingDevice("c1"), DeviceCommand.parse(JSONObject("""{"id":"c1","type":"ring_device"}""")))
    }

    @Test
    fun unknownTypeBecomesInvalid() {
        val parsed = cmd("format_phone")
        assertTrue(parsed is DeviceCommand.Invalid)
        assertEquals("format_phone", (parsed as DeviceCommand.Invalid).type)
    }

    @Test
    fun invalidPayloadsBecomeInvalid() {
        assertTrue(cmd("show_message", """{"text":""}""") is DeviceCommand.Invalid)
        assertTrue(cmd("show_message", "{}") is DeviceCommand.Invalid)
        assertTrue(cmd("show_message", """{"text":"${"x".repeat(501)}"}""") is DeviceCommand.Invalid)
        assertTrue(cmd("set_volume", """{"level":101}""") is DeviceCommand.Invalid)
        assertTrue(cmd("set_volume", """{"level":-1}""") is DeviceCommand.Invalid)
        assertTrue(cmd("set_volume", """{"level":50.5}""") is DeviceCommand.Invalid)
        assertTrue(cmd("set_volume", """{"level":"50"}""") is DeviceCommand.Invalid)
        assertTrue(cmd("update_emergency_number", """{"e164":"9876543210"}""") is DeviceCommand.Invalid)
        assertTrue(cmd("update_emergency_number", """{"e164":"+0123456789"}""") is DeviceCommand.Invalid)
    }

    @Test
    fun commandWithoutIdIsSkipped() {
        assertNull(DeviceCommand.parse(JSONObject("""{"type":"ring_device"}""")))
    }

    @Test
    fun unknownTypeExecutesAsFailedResult() {
        val manager = CommandExecutionManager(ApplicationProvider.getApplicationContext(), NoTokens)
        val result = manager.execute(cmd("format_phone")!!).toJson()
        assertFalse(result.getBoolean("success"))
        assertTrue(result.getString("message").contains("format_phone"))
    }

    @Test
    fun reportLocationWithoutPermissionFails() {
        val manager = CommandExecutionManager(ApplicationProvider.getApplicationContext(), NoTokens)
        val result = manager.execute(DeviceCommand.ReportLocation("c1"))
        assertFalse(result.success)
    }

    @Test
    fun updateEmergencyNumberIsSaved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = CommandExecutionManager(context, NoTokens)
        val result = manager.execute(DeviceCommand.UpdateEmergencyNumber("c1", "+919876543210"))
        assertTrue(result.success)
        assertEquals("+919876543210", com.grandma.launcher.data.AppPreferences(context).emergencyNumber)
    }

    private object NoTokens : com.grandma.launcher.network.DeviceTokenProvider {
        override suspend fun currentToken() =
            com.grandma.launcher.network.ApiClient.Result.Error(401, "no token")

        override suspend fun refreshToken(rejectedToken: String?) =
            com.grandma.launcher.network.ApiClient.Result.Error(401, "no token")
    }
}
