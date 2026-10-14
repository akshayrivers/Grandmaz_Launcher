package com.grandma.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherData(
    val temperatureCelsius: Int,
    val condition: WeatherCondition,
    val locationName: String
)

enum class WeatherCondition {
    SUNNY, CLOUDY, RAINY, THUNDERSTORM, SNOWY
}

/**
 * Real weather from Open-Meteo. A caretaker-set city is used first; otherwise the last
 * coarse location. Results are cached for 45 minutes. No API key.
 */
class WeatherRepository(private val context: Context) {

    fun getCurrentWeather(): WeatherData = readCache()?.data ?: WeatherData(0, WeatherCondition.CLOUDY, cityLabel())

    suspend fun refresh(): WeatherData = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (cached != null && System.currentTimeMillis() - cached.savedAt < CACHE_MS) {
            return@withContext cached.data
        }
        val prefs = AppPreferences(context)
        val coords = when {
            prefs.weatherMode != "location" && prefs.weatherCity.isNotBlank() -> geocode(prefs.weatherCity)
            else -> coarseLocation() ?: prefs.weatherCity.takeIf { it.isNotBlank() }?.let { geocode(it) }
        } ?: return@withContext getCurrentWeather()
        val fetched = fetch(coords) ?: return@withContext getCurrentWeather()
        writeCache(fetched)
        fetched
    }

    private fun cityLabel(): String = AppPreferences(context).weatherCity

    private fun geocode(city: String): Pair<Double, Double>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(city, "UTF-8")}&count=1"
        val json = getJson(url) ?: return null
        val first = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        return first.optDouble("latitude") to first.optDouble("longitude")
    }

    private fun fetch(coords: Pair<Double, Double>): WeatherData? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${coords.first}&longitude=${coords.second}&current=temperature_2m,weather_code"
        val current = getJson(url)?.optJSONObject("current") ?: return null
        val temp = current.optDouble("temperature_2m", Double.NaN)
        if (temp.isNaN()) return null
        return WeatherData(temp.toInt(), condition(current.optInt("weather_code")), cityLabel())
    }

    private fun coarseLocation(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
            .firstNotNullOfOrNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            } ?: return null
        return location.latitude to location.longitude
    }

    private fun getJson(url: String): JSONObject? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONObject(connection.inputStream.bufferedReader().readText())
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun condition(code: Int): WeatherCondition = when (code) {
        0, 1 -> WeatherCondition.SUNNY
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOWY
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAINY
        else -> WeatherCondition.CLOUDY
    }

    private fun readCache(): Cached? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong(KEY_AT, 0L)
        if (savedAt == 0L) return null
        val condition = runCatching { WeatherCondition.valueOf(prefs.getString(KEY_CONDITION, "CLOUDY")!!) }.getOrDefault(WeatherCondition.CLOUDY)
        return Cached(savedAt, WeatherData(prefs.getInt(KEY_TEMP, 0), condition, prefs.getString(KEY_CITY, "").orEmpty()))
    }

    private fun writeCache(data: WeatherData) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_AT, System.currentTimeMillis())
            .putInt(KEY_TEMP, data.temperatureCelsius)
            .putString(KEY_CONDITION, data.condition.name)
            .putString(KEY_CITY, data.locationName)
            .apply()
    }

    private data class Cached(val savedAt: Long, val data: WeatherData)

    companion object {
        private const val CACHE_MS = 45 * 60 * 1000L
        private const val PREFS = "grandma_weather_cache"
        private const val KEY_AT = "saved_at"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_CITY = "city"
    }
}
