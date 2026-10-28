package com.grandma.launcher.ui.caretaker

import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import com.grandma.launcher.network.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaretakerConfigActivityTest {

    @Test
    fun addCaretakerStaysOnScreenWhenOffline() {
        val activity = Robolectric.buildActivity(CaretakerConfigActivity::class.java).setup().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.etCaretakerEmail).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnAddCaretaker).visibility)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tvOffline).visibility)
        assertEquals(
            activity.getString(R.string.caretaker_offline),
            activity.findViewById<android.widget.TextView>(R.id.tvOffline).text.toString()
        )
        val city = activity.findViewById<EditText>(R.id.etWeatherCity)
        assertEquals(ContextCompat.getColor(activity, R.color.color_text_primary), city.currentTextColor)
        assertTrue(city.isShown)
    }

    @Test
    fun networkFailureSaysThePhoneIsOffline() {
        val message = CaretakerConfigActivity.caretakerInviteMessage(
            ApiClient.Result.Error(ApiClient.NETWORK_ERROR, "down")
        )
        assertEquals(R.string.caretaker_offline, message)
    }

    @Test
    fun alreadyLinkedIsNotReportedAsOffline() {
        val message = CaretakerConfigActivity.caretakerInviteMessage(
            ApiClient.Result.Error(409, "linked", "ALREADY_LINKED")
        )
        assertEquals(R.string.caretaker_already_linked, message)
    }
}
