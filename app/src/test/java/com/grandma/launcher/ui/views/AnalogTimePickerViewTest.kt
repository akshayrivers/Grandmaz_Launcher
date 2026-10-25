package com.grandma.launcher.ui.views

import android.app.Activity
import com.grandma.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnalogTimePickerViewTest {

    @Test
    fun pickerNamesHandsSunAndMoon() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val picker = AnalogTimePickerView(activity)
        assertEquals(
            "Alarm time. Move the hands. Sun is morning, moon is night.",
            activity.getString(R.string.alarm_time_picker),
        )
        assertEquals(activity.getString(R.string.alarm_time_picker), picker.contentDescription)
    }
}
