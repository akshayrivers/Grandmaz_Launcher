package com.grandma.launcher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.remote.DeviceLifecycleRepository
import com.grandma.launcher.remote.DeviceSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrandmaApplication : Application() {

    /**
     * Process-wide scope for work that must outlive an Activity (registration, invitation
     * creation, SOS help request while the dialer is in front).
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        val prefs = AppPreferences(this)
        prefs.migrateLegacySecurityState()
        if (prefs.uninstallNoticePending) {
            prefs.uninstallNoticePending = false
            appScope.launch { DeviceLifecycleRepository(this@GrandmaApplication).reportStillInstalled() }
        }
        if (prefs.isSetupComplete) {
            DeviceSyncManager.getInstance(this).schedulePeriodicSync()
        }
    }
}

val android.content.Context.appScope: CoroutineScope
    get() = (applicationContext as GrandmaApplication).appScope
