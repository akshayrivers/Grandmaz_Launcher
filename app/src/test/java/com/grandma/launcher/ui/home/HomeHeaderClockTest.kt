package com.grandma.launcher.ui.home

import android.app.Activity
import android.view.View
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.ui.alarm.AlarmActivity
import com.grandma.launcher.ui.views.AnalogClockView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeHeaderClockTest {

    @Test
    fun clockTapOpensTheAlarmSetter() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        AppPreferences(activity).localFlowEnabled = true
        val header = HomeHeaderView(activity)
        val clock = (0 until header.childCount)
            .map { header.getChildAt(it) }
            .first { it is AnalogClockView }
        assertTrue(clock.performClick())
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(AlarmActivity::class.java.name, started.component?.className)
        assertTrue(started.getBooleanExtra(AlarmActivity.EXTRA_OPEN_SETTER, false))
    }

    @Test
    fun clockLongPressReachesTheHelper() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val header = HomeHeaderView(activity)
        val clock = (0 until header.childCount)
            .map { header.getChildAt(it) }
            .first { it is AnalogClockView }
        var held = false
        header.onClockLongPress = { held = true }
        assertTrue(clock.performLongClick())
        assertTrue(held)
    }

    @Test
    fun clockIsClickableAboveTheHeader() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val header = HomeHeaderView(activity)
        val clock = (0 until header.childCount)
            .map { header.getChildAt(it) }
            .first { it is AnalogClockView }
        assertEquals(View.VISIBLE, clock.visibility)
        assertTrue(clock.isClickable)
    }
}
