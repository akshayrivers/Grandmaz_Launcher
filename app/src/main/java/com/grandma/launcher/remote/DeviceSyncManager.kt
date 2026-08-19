package com.grandma.launcher.remote

import android.content.Context
import android.util.Log
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Central manager that coordinates device registration, state snapshot posting,
 * and remote command polling with the Fastify backend.
 */
class DeviceSyncManager(private val context: Context) {

    private val appPrefs = AppPreferences(context)
    private val registrationRepo = DeviceRegistrationRepository(context)
    private val stateSnapshotRepo = StateSnapshotRepository(context)
    private val commandManager = CommandExecutionManager(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isLoopRunning = false

    /**
     * Initializes sync loop. Registers device if needed, posts snapshot, and begins command polling.
     */
    fun startSyncLoop() {
        if (isLoopRunning) return
        isLoopRunning = true

        scope.launch {
            // Initial registration & challenge verification check
            ensureRegisteredAndVerified()

            while (isActive && isLoopRunning) {
                try {
                    // Post state snapshot
                    stateSnapshotRepo.postSnapshot()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in DeviceSyncManager loop: ${e.localizedMessage}", e)
                }

                // Poll every 30 seconds while active
                delay(30000L)
            }
        }
    }

    fun stopSyncLoop() {
        isLoopRunning = false
    }

    suspend fun ensureRegisteredAndVerified(): Boolean {
        return try {
            val result = registrationRepo.registerAndVerifyDevice()
            result is ApiClient.Result.Success && result.data
        } catch (e: Exception) {
            Log.e(TAG, "Device registration failed: ${e.localizedMessage}", e)
            false
        }
    }

    suspend fun triggerManualSync() {
        scope.launch {
            registrationRepo.registerAndVerifyDevice()
            stateSnapshotRepo.postSnapshot()
            commandManager.pollAndExecuteCommands()
        }
    }

    companion object {
        private const val TAG = "DeviceSyncManager"

        @Volatile
        private var INSTANCE: DeviceSyncManager? = null

        fun getInstance(context: Context): DeviceSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
