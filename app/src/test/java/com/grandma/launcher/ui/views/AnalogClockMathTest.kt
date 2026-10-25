package com.grandma.launcher.ui.views

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalogClockMathTest {

    @Test
    fun threeOClockPointsTheHourHandAtThree() {
        val hands = AnalogClockMath.hands(3, 0, 0, 0)
        assertEquals(90f, hands.hourDegrees, 0.01f)
        assertEquals(0f, hands.minuteDegrees, 0.01f)
        assertEquals(0f, hands.secondDegrees, 0.01f)
    }

    @Test
    fun halfPastTwelveMovesTheHourHand() {
        val hands = AnalogClockMath.hands(0, 30, 0, 0)
        assertEquals(15f, hands.hourDegrees, 0.01f)
        assertEquals(180f, hands.minuteDegrees, 0.01f)
        assertEquals(0f, hands.secondDegrees, 0.01f)
    }

    @Test
    fun secondsMoveTheMinuteHand() {
        val hands = AnalogClockMath.hands(0, 0, 30, 0)
        assertEquals(3f, hands.minuteDegrees, 0.01f)
        assertEquals(180f, hands.secondDegrees, 0.01f)
    }

    @Test
    fun noonAndMidnightUseTheSameFace() {
        val noon = AnalogClockMath.hands(0, 0, 0, 0)
        val wrapped = AnalogClockMath.hands(12, 0, 0, 0)
        assertEquals(noon.hourDegrees, wrapped.hourDegrees, 0.01f)
    }
}
