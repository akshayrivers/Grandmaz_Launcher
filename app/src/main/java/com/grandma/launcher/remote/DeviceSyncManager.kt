package com.grandma.launcher.remote

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Coordinates backend sync: device token, profile (/me), push token, state snapshot and
 * pending caretaker commands. Runs as WorkManager work so it survives process death:
 * a unique 15-minute periodic job plus on-demand one-time syncs.
 */
class DeviceSyncManager private constructor(private val context: Context) {

    private val appPrefs = AppPreferences(context)
    private val tokens = DeviceRegistrationRepository(context)
    private val profileRepo = DeviceProfileRepository(context, tokens)
    private val snapshotRepo = StateSnapshotRepository(context, tokens)
    private val commandManager = CommandExecutionManager(context, tokens)
    private val syncMutex = Mutex()

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<DeviceSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Enqueues an immediate sync. Without [force], calls within [MIN_ON_DEMAND_INTERVAL_MS]
     * of the last successful sync are dropped (Home's onResume fires often).
     */
    fun requestSyncNow(force: Boolean = false) {
        if (!appPrefs.isSetupComplete && !force) return
        if (!force && System.currentTimeMillis() - appPrefs.lastSyncTimestamp < MIN_ON_DEMAND_INTERVAL_MS) return
        val request = OneTimeWorkRequestBuilder<DeviceSyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** One full sync pass. Returns false when it should be retried (network/server trouble). */
    suspend fun runSync(): Boolean = syncMutex.withLock {
        val auth = tokens.currentToken()
        if (auth is ApiClient.Result.Error) {
            Log.e(TAG, "Device authentication failed: ${auth.statusCode} ${auth.code}")
            return@withLock false
        }

        logIfError("profile", profileRepo.refreshProfile())
        appPrefs.fcmToken.takeIf { it.isNotBlank() }?.let { logIfError("push-token", profileRepo.updatePushToken(it)) }
        val snapshot = snapshotRepo.postSnapshot()
        logIfError("snapshot", snapshot)
        val commands = commandManager.pollAndExecuteCommands()
        logIfError("commands", commands)
        val config = DeviceConfigRepository(context, tokens).sync()
        logIfError("config", config)

        snapshot !is ApiClient.Result.Error && commands !is ApiClient.Result.Error && config !is ApiClient.Result.Error
    }

    private fun logIfError(step: String, result: ApiClient.Result<*>) {
        if (result is ApiClient.Result.Error) {
            Log.e(TAG, "Sync step $step failed: ${result.statusCode} ${result.code}")
        }
    }

    private fun networkConstraints() =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        private const val TAG = "DeviceSyncManager"
        private const val PERIODIC_WORK_NAME = "grandma_device_sync_periodic"
        private const val ONE_TIME_WORK_NAME = "grandma_device_sync_now"
        private const val MIN_ON_DEMAND_INTERVAL_MS = 60_000L

        @Volatile
        private var INSTANCE: DeviceSyncManager? = null

        fun getInstance(context: Context): DeviceSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

class DeviceSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ok = try {
            DeviceSyncManager.getInstance(applicationContext).runSync()
        } catch (e: Exception) {
            Log.e(TAG, "Sync crashed", e)
            false
        }
        return when {
            ok -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val TAG = "DeviceSyncWorker"
        const val MAX_RETRIES = 3
    }
}
