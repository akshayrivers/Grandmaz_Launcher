package com.grandma.launcher.ui.caretaker

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaretakerHelpActivityTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("grandma_app_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        AppPreferences(context).setLinkedCaretakers(
            listOf("local-caretaker@localhost" to "primary")
        )
    }

    @Test
    fun helpPageShowsWhoWillBeTold() {
        val activity = Robolectric.buildActivity(CaretakerHelpActivity::class.java).setup().get()

        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnSendHelp).visibility)
        assertEquals(
            activity.getString(R.string.caretaker_help_subtitle),
            activity.findViewById<TextView>(R.id.tvHelpSubtitle).text.toString()
        )
        val people = activity.findViewById<TextView>(R.id.tvHelpPeople).text.toString()
        assertTrue(people.contains("local-caretaker@localhost"))
        assertTrue(people.contains("primary"))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.btnEditHome).visibility)
    }
}
