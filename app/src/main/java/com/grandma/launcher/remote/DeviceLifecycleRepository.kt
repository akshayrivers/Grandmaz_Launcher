package com.grandma.launcher.remote

import android.content.Context
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import org.json.JSONObject

/**
 * Tells the backend this launcher is being removed. The device row stays so a caretaker
 * can still see that the app was uninstalled. Android does not run app code after a
 * Settings uninstall, so this is sent first and the system uninstall screen opens after.
 */
class DeviceLifecycleRepository(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context)
) {

    suspend fun reportUninstalled(): ApiClient.Result<Unit> {
        return when (val result = ApiClient.deviceRequest("POST", "api/device/uninstall", JSONObject(), tokens)) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> ApiClient.Result.Success(Unit)
        }
    }

    /** The app is still on the phone, so the uninstall notice should not stay up. */
    suspend fun reportStillInstalled(): ApiClient.Result<Unit> {
        return when (val result = ApiClient.deviceRequest("POST", "api/device/installed", JSONObject(), tokens)) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> ApiClient.Result.Success(Unit)
        }
    }
}
