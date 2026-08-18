package com.grandma.launcher.remote

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Collects hardware & app state metrics and posts snapshots to the backend.
 */
class StateSnapshotRepository(private val context: Context) {

    private val appPrefs = AppPreferences(context)

    suspend fun postSnapshot(): ApiClient.Result<JSONObject> = withContext(Dispatchers.IO) {
        val baseUrl = appPrefs.backendBaseUrl
        val deviceId = appPrefs.deviceId

        val batteryInfo = getBatteryInfo()
        val wifiSsid = getWifiSsid()
        val storageFreeMb = getFreeStorageMb()
        val installedApps = getInstalledAppsJson()
        val settings = getSettingsJson()

        val body = JSONObject().apply {
            put("deviceId", deviceId)
            put("batteryLevel", batteryInfo.first)
            put("batteryStatus", batteryInfo.second)
            if (wifiSsid != null) {
                put("wifiSsid", wifiSsid)
            }
            put("storageFreeMb", storageFreeMb)
            put("installedApps", installedApps)
            put("settings", settings)
            put("snapshotData", JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("caretakerEmail", appPrefs.caretakerEmail)
                put("emergencyNumber", appPrefs.emergencyNumber)
            })
        }

        val result = ApiClient.postJson(baseUrl, "api/shared-state/snapshot", body)
        if (result is ApiClient.Result.Success) {
            appPrefs.lastSyncTimestamp = System.currentTimeMillis()
        }
        result
    }

    private fun getBatteryInfo(): Pair<Int, String> {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatusIntent = context.registerReceiver(null, intentFilter)
            val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

            val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val statusStr = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            }
            Pair(batteryPct, statusStr)
        } catch (e: Exception) {
            Pair(0, "unknown")
        }
    }

    private fun getWifiSsid(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "")
            if (ssid != null && ssid != "<unknown ssid>" && ssid.isNotEmpty()) ssid else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getFreeStorageMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes / (1024 * 1024)
        } catch (e: Exception) {
            0L
        }
    }

    private fun getInstalledAppsJson(): JSONArray {
        val jsonArray = JSONArray()
        try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            for (app in apps) {
                if (app.activityInfo.packageName != context.packageName) {
                    val appObj = JSONObject().apply {
                        put("label", app.loadLabel(pm).toString())
                        put("packageName", app.activityInfo.packageName)
                    }
                    jsonArray.put(appObj)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return jsonArray
    }

    private fun getSettingsJson(): JSONObject {
        return JSONObject().apply {
            put("emergencyNumber", appPrefs.emergencyNumber)
            put("caretakerEmail", appPrefs.caretakerEmail)
            put("fabIdleDelayMs", appPrefs.fabIdleDelayMs)
            put("isSetupComplete", appPrefs.isSetupComplete)
            put("isDeviceVerified", appPrefs.isDeviceVerified)
        }
    }
}
