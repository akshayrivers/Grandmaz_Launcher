package com.grandma.launcher.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * After the SOS call has already started, posts an alert with location and keeps
 * sending fixes until the alert is cancelled. Cancellation never hangs up the call.
 * If the post fails, texts every known caretaker a maps link.
 */
class SosCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val tokens: com.grandma.launcher.network.DeviceTokenProvider = DeviceRegistrationRepository(context)
) {
    private val appContext = context.applicationContext
    private val prefs = AppPreferences(appContext)
    private var listener: LocationListener? = null

    fun start() {
        val fix = lastKnownLocation()
        scope.launch {
            val body = JSONObject()
            if (fix != null) {
                body.put("latitude", fix.latitude)
                body.put("longitude", fix.longitude)
                if (fix.hasAccuracy()) body.put("accuracyM", fix.accuracy.toInt())
            }
            when (val result = ApiClient.deviceRequest("POST", "api/device/sos", body, tokens)) {
                is ApiClient.Result.Success -> {
                    val id = (result.data as? JSONObject)?.optString("id").orEmpty()
                    if (id.isNotBlank()) {
                        prefs.activeSosEventId = id
                        watchLocation(id)
                    }
                }
                is ApiClient.Result.Error -> {
                    Log.e(TAG, "SOS alert failed: ${result.statusCode} ${result.code}")
                    if (prefs.smsFallbackEnabled) sendSms(fix)
                }
            }
        }
    }

    fun cancel() {
        stopWatching()
        val id = prefs.activeSosEventId
        prefs.activeSosEventId = ""
        if (id.isBlank()) return
        scope.launch {
            ApiClient.deviceRequest("POST", "api/device/sos/$id/cancel", JSONObject(), tokens)
        }
    }

    fun isActive(): Boolean = prefs.activeSosEventId.isNotBlank()

    @SuppressLint("MissingPermission")
    private fun watchLocation(eventId: String) {
        if (!hasLocationPermission()) return
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        stopWatching()
        val next = LocationListener { location ->
            scope.launch {
                val body = JSONObject()
                    .put("latitude", location.latitude)
                    .put("longitude", location.longitude)
                if (location.hasAccuracy()) body.put("accuracyM", location.accuracy.toInt())
                val result = ApiClient.deviceRequest("POST", "api/device/sos/$eventId/location", body, tokens)
                if (result is ApiClient.Result.Error && result.code == "SOS_CLOSED") {
                    prefs.activeSosEventId = ""
                    stopWatching()
                }
            }
        }
        listener = next
        manager.requestLocationUpdates(provider, 15_000L, 0f, next)
    }

    private fun stopWatching() {
        val current = listener ?: return
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.removeUpdates(current)
        listener = null
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
            }
            .maxByOrNull { it.time }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun sendSms(fix: Location?) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SMS fallback skipped: SEND_SMS is not granted")
            return
        }
        val link = if (fix != null) " https://maps.google.com/?q=${fix.latitude},${fix.longitude}" else ""
        val text = "SOS from grandma's phone.$link"
        val phones = prefs.caretakerPhones
        if (phones.isEmpty()) return
        val sms = SmsManager.getDefault()
        phones.forEach { phone ->
            try {
                sms.sendTextMessage(phone, null, text, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "SMS fallback failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "SosCoordinator"

        fun phonesFromCaretakers(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val phone = array.optJSONObject(i)?.optString("phoneE164").orEmpty()
                phone.takeIf { it.startsWith("+") }
            }.distinct()
        }
    }
}
