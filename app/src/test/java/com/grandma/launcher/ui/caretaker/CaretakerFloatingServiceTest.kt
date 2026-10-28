package com.grandma.launcher.ui.caretaker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaretakerFloatingServiceTest {

    @Test
    fun aSmallMoveIsStillATap() {
        assertFalse(CaretakerFloatingService.isDrag(4f, -3f, slop = 8))
    }

    @Test
    fun aMovePastTheSlopIsADrag() {
        assertTrue(CaretakerFloatingService.isDrag(9f, 0f, slop = 8))
        assertTrue(CaretakerFloatingService.isDrag(0f, -12f, slop = 8))
    }

    @Test
    fun buttonsStayOnScreen() {
        assertEquals(0 to 0, CaretakerFloatingService.clampToScreen(-40, -10, 400, 800, 80))
        assertEquals(320 to 720, CaretakerFloatingService.clampToScreen(900, 900, 400, 800, 80))
    }

    @Test
    fun helpAndHomeStartInOppositeBottomCorners() {
        val help = CaretakerFloatingService.cornerPosition(1080, 2340, 80, 16, 24, alignEnd = false)
        val home = CaretakerFloatingService.cornerPosition(1080, 2340, 80, 16, 24, alignEnd = true)
        assertEquals(16 to 2236, help)
        assertEquals(984 to 2236, home)
    }
}
