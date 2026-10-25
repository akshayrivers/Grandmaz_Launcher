package com.grandma.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeatherRepositoryTest {

    @Test
    fun parseOpenMeteoCurrentSample() {
        val body = """
            {"current":{"time":"2026-10-25T18:00","temperature_2m":31.6,"weather_code":0}}
        """.trimIndent()
        val data = parseOpenMeteoCurrent(body, "")
        assertEquals(31, data!!.temperatureCelsius)
        assertEquals(WeatherCondition.SUNNY, data.condition)
        assertEquals("", data.locationName)
        assertTrue(data.known)
        assertEquals(WeatherCondition.RAINY, weatherCondition(61))
        assertNull(parseOpenMeteoCurrent("{}", ""))
    }
}
