package com.grandma.launcher.data

import android.content.Context
import java.util.Calendar

data class WeatherData(
    val temperatureCelsius: Int,
    val condition: WeatherCondition,
    val locationName: String
)

enum class WeatherCondition {
    SUNNY, CLOUDY, RAINY, THUNDERSTORM, SNOWY
}

/**
 * Repository to simulate local weather based on time of day.
 * Avoids network latency, permission prompts, and API key dependencies.
 * Location defaults to Jammu, matching the Dogri dialect context.
 */
class WeatherRepository(private val context: Context) {

    fun getCurrentWeather(): WeatherData {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val (temp, condition) = when (hour) {
            in 6..10 -> Pair(24, WeatherCondition.SUNNY)       // Sunny morning
            in 11..15 -> Pair(32, WeatherCondition.CLOUDY)     // Cloudy warm afternoon
            in 16..19 -> Pair(27, WeatherCondition.RAINY)      // Cool rainy evening
            in 20..23 -> Pair(22, WeatherCondition.THUNDERSTORM) // Thunderstorm night
            else -> Pair(18, WeatherCondition.CLOUDY)          // Cool night
        }

        return WeatherData(temp, condition, "Jammu")
    }
}
