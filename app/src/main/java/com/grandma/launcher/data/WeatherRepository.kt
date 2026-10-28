package com.grandma.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executor
import kotlin.coroutines.resume

data class WeatherData(
    val temperatureCelsius: Int,
    val condition: WeatherCondition,
    val locationName: String,
    val known: Boolean = true
)

enum class WeatherCondition {
    SUNNY, CLOUDY, RAINY, THUNDERSTORM, SNOWY
}

/** Open-Meteo `current` object. Location name is supplied by the caller, never from coordinates. */
fun parseOpenMeteoCurrent(body: String, locationName: String = ""): WeatherData? {
    val current = runCatching { JSONObject(body).optJSONObject("current") }.getOrNull() ?: return null
    val temp = current.optDouble("temperature_2m", Double.NaN)
    if (temp.isNaN()) return null
    return WeatherData(temp.toInt(), weatherCondition(current.optInt("weather_code")), locationName, known = true)
}

fun weatherCondition(code: Int): WeatherCondition = when (code) {
    0, 1 -> WeatherCondition.SUNNY
    71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOWY
    95, 96, 99 -> WeatherCondition.THUNDERSTORM
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAINY
    else -> WeatherCondition.CLOUDY
}

/**
 * Real weather from Open-Meteo. A caretaker-set city is used first. Otherwise a coarse
 * network fix is requested when the last one is missing or old. Results are cached for
 * 45 minutes. No API key. Coordinates are not logged.
 */
class WeatherRepository(private val context: Context) {

    fun getCurrentWeather(): WeatherData = readCache()?.data ?: WeatherData(0, WeatherCondition.CLOUDY, "", known = false)

    suspend fun refresh(): WeatherData = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (cached != null && System.currentTimeMillis() - cached.savedAt < CACHE_MS) {
            return@withContext cached.data
        }
        val prefs = AppPreferences(context)
        val city = prefs.weatherCity.trim()
        val coords = when {
            prefs.weatherMode != "location" && city.isNotBlank() -> geocode(city)
            else -> coarseLocation() ?: city.takeIf { it.isNotBlank() }?.let { geocode(it) }
        } ?: return@withContext unknown()
        val fetched = fetch(coords) ?: return@withContext unknown()
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
        val body = getText(url) ?: return null
        val name = if (AppPreferences(context).weatherMode != "location" && AppPreferences(context).weatherCity.isNotBlank()) {
            cityLabel()
        } else {
            ""
        }
        return parseOpenMeteoCurrent(body, name)
    }

    private fun unknown(): WeatherData {
        val cached = readCache()
        return if (cached != null && System.currentTimeMillis() - cached.savedAt < CACHE_MS) cached.data
        else WeatherData(0, WeatherCondition.CLOUDY, "", known = false)
    }

    @SuppressLint("MissingPermission")
    private suspend fun coarseLocation(): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val last = lastKnown(manager)
        if (last != null && System.currentTimeMillis() - last.time < FIX_MAX_AGE_MS) {
            return last.latitude to last.longitude
        }
        val fresh = withTimeoutOrNull(FIX_WAIT_MS) { currentFix(manager) }
        val chosen = fresh ?: last
        return chosen?.let { it.latitude to it.longitude }
    }

    private fun lastKnown(manager: LocationManager): Location? {
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(manager: LocationManager): Location? = suspendCancellableCoroutine { cont ->
        val provider = when {
            runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            val executor = Executor { command -> android.os.Handler(Looper.getMainLooper()).post(command) }
            try {
                manager.getCurrentLocation(provider, signal, executor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            }
            return@suspendCancellableCoroutine
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if (cont.isActive) cont.resume(location)
            }
            @Deprecated("Legacy callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                manager.removeUpdates(this)
                if (cont.isActive) cont.resume(null)
            }
        }
        cont.invokeOnCancellation { manager.removeUpdates(listener) }
        try {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun getJson(url: String): JSONObject? {
        val text = getText(url) ?: return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun getText(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(): Cached? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedAt = prefs.getLong(KEY_AT, 0L)
        if (savedAt == 0L) return null
        val condition = runCatching { WeatherCondition.valueOf(prefs.getString(KEY_CONDITION, "CLOUDY")!!) }.getOrDefault(WeatherCondition.CLOUDY)
        return Cached(
            savedAt,
            WeatherData(prefs.getInt(KEY_TEMP, 0), condition, prefs.getString(KEY_CITY, "").orEmpty(), known = true)
        )
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
        private const val FIX_MAX_AGE_MS = 30 * 60 * 1000L
        private const val FIX_WAIT_MS = 12_000L
        private const val PREFS = "grandma_weather_cache"
        private const val KEY_AT = "saved_at"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_CITY = "city"
    }
}
