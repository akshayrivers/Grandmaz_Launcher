package com.grandma.launcher.ui.common

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneCallerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun buildsCallOrDialIntent() {
        val call = PhoneCaller.buildCallIntent("+919876543210", canCallDirectly = true)
        assertEquals(Intent.ACTION_CALL, call.action)
        assertEquals("tel", call.data!!.scheme)
        assertEquals("+919876543210", call.data!!.schemeSpecificPart)

        assertEquals(Intent.ACTION_DIAL, PhoneCaller.buildCallIntent("112", canCallDirectly = false).action)
    }

    @Test
    fun usesDialerWithoutCallPermission() {
        shadowOf(app).denyPermissions(Manifest.permission.CALL_PHONE)
        assertTrue(PhoneCaller.placeCall(app, "112"))
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_DIAL, started.action)
        assertEquals("112", started.data!!.schemeSpecificPart)
    }

    @Test
    fun callsDirectlyWithCallPermission() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        assertTrue(PhoneCaller.placeCall(app, "112"))
        assertEquals(Intent.ACTION_CALL, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun blankNumberDoesNothing() {
        assertFalse(PhoneCaller.placeCall(app, " "))
    }
}
