package com.grandma.launcher.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HelpRequestRepositoryTest {

    @Test
    fun generalRequestMatchesDeviceHelpContract() {
        val body = HelpRequestRepository.buildBody(
            title = "Help requested from launcher",
            description = null,
            type = HelpRequestRepository.HelpType.GENERAL
        )
        assertEquals("Help requested from launcher", body.getString("title"))
        assertEquals("general", body.getString("type"))
        assertFalse(body.has("description"))
        assertFalse(body.has("deviceId"))
    }

    @Test
    fun descriptionIsSentWhenPresent() {
        val body = HelpRequestRepository.buildBody(
            "Need a person",
            "She tapped help",
            HelpRequestRepository.HelpType.TECH_SUPPORT
        )
        assertEquals("tech_support", body.getString("type"))
        assertEquals("She tapped help", body.getString("description"))
    }

    @Test
    fun blankDescriptionIsOmitted() {
        val body = HelpRequestRepository.buildBody("Help", "   ", HelpRequestRepository.HelpType.MEDICAL)
        assertEquals("medical", body.getString("type"))
        assertFalse(body.has("description"))
    }

    @Test
    fun sosTypeStaysDistinctFromGeneralHelp() {
        val body = HelpRequestRepository.buildBody("SOS", null, HelpRequestRepository.HelpType.SOS)
        assertEquals("sos", body.getString("type"))
        assertFalse(JSONObject(body.toString()).has("deviceId"))
        assertFalse(body.has("pin"))
        assertFalse(body.has("caretakerPin"))
    }

    @Test
    fun localModeRecordsHelpWithoutCallingTheNetwork() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LocalHelpStore.clear(context)
        val prefs = AppPreferences(context)
        prefs.localFlowEnabled = true
        prefs.localFlowFailNextHelp = false
        val result = runBlocking {
            HelpRequestRepository(context).createHelpRequest(
                title = "Help requested from launcher",
                type = HelpRequestRepository.HelpType.GENERAL
            )
        }
        assertTrue(result is ApiClient.Result.Success)
        val saved = LocalHelpStore.entries(context)
        assertEquals(1, saved.size)
        assertEquals("general", saved.single().type)
        val body = HelpRequestRepository.buildBody(
            "Help requested from launcher",
            null,
            HelpRequestRepository.HelpType.GENERAL
        )
        assertFalse(body.has("deviceId"))
        assertFalse(body.has("pin"))
    }

    @Test
    fun localModeFailureLeavesHerAbleToRetry() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        LocalHelpStore.clear(context)
        val prefs = AppPreferences(context)
        prefs.localFlowEnabled = true
        prefs.localFlowFailNextHelp = true
        val before = LocalHelpStore.entries(context).size
        val result = runBlocking {
            HelpRequestRepository(context).createHelpRequest("Help requested from launcher")
        }
        assertTrue(result is ApiClient.Result.Error)
        assertEquals(before, LocalHelpStore.entries(context).size)
    }
}
