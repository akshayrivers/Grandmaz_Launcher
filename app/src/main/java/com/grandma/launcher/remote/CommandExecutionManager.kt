package com.grandma.launcher.remote

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import com.grandma.launcher.ui.message.MessageActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Fetches pending caretaker commands, executes them and reports
 * `running` → `completed` / `failed` with a `{ success, message, ... }` result.
 */
class CommandExecutionManager(
    context: Context,
    private val tokens: DeviceTokenProvider = DeviceRegistrationRepository(context)
) {

    private val context = context.applicationContext
    private val appPrefs = AppPreferences(context)

    /** Returns the number of commands executed. */
    suspend fun pollAndExecuteCommands(): ApiClient.Result<Int> = withContext(Dispatchers.IO) {
        val result = ApiClient.deviceRequest("GET", "api/device/commands?status=pending", null, tokens)
        if (result is ApiClient.Result.Error) return@withContext result

        val commands = (result as ApiClient.Result.Success).data as? JSONArray ?: JSONArray()
        var executed = 0
        for (i in 0 until commands.length()) {
            val command = commands.optJSONObject(i)?.let { DeviceCommand.parse(it) } ?: continue

            // A command we cannot claim (already finished elsewhere, or network failure)
            // is not executed; a still-pending one is returned again on the next poll.
            if (reportStatus(command.id, STATUS_RUNNING, null) is ApiClient.Result.Error) continue

            val outcome = try {
                execute(command)
            } catch (e: Exception) {
                Log.e(TAG, "Command ${command.id} threw", e)
                CommandResult.failed("Phone could not run the command")
            }
            reportStatus(command.id, if (outcome.success) STATUS_COMPLETED else STATUS_FAILED, outcome.toJson())
            executed++
        }
        ApiClient.Result.Success(executed)
    }

    internal fun execute(command: DeviceCommand): CommandResult = when (command) {
        is DeviceCommand.RingDevice -> ringDevice()
        is DeviceCommand.SyncNow -> {
            DeviceSyncManager.getInstance(context).requestSyncNow(force = true)
            CommandResult.ok("Sync scheduled")
        }
        is DeviceCommand.ReportLocation -> reportLocation()
        is DeviceCommand.ShowMessage -> showMessage(command.text)
        is DeviceCommand.SetVolume -> setVolume(command.level)
        is DeviceCommand.UpdateEmergencyNumber -> {
            appPrefs.emergencyNumber = command.e164
            CommandResult.ok("Emergency number updated")
        }
        is DeviceCommand.Invalid -> CommandResult.failed(command.reason)
    }

    private suspend fun reportStatus(id: String, status: String, result: JSONObject?): ApiClient.Result<Any?> {
        val body = JSONObject().put("status", status)
        if (result != null) body.put("result", result)
        val response = ApiClient.deviceRequest("PATCH", "api/device/commands/$id/status", body, tokens)
        if (response is ApiClient.Result.Error) {
            Log.e(TAG, "Status $status for command $id rejected: ${response.statusCode} ${response.code}")
        }
        return response
    }

    // ── ring_device ──────────────────────────────────────────────────────────

    private fun ringDevice(): CommandResult {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return CommandResult.failed("No ringtone available on the phone")

        val audioManager = context.getSystemService(AudioManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            val generation = synchronized(ringLock) {
                stopRingingLocked(audioManager)
                ringGeneration++
                try {
                    previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                        0
                    )
                    activePlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, uri)
                        isLooping = true
                        prepare()
                        start()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not play ringtone", e)
                }
                ringGeneration
            }
            vibrate(longArrayOf(0, 800, 400), RING_DURATION_MS)
            handler.postDelayed({
                synchronized(ringLock) {
                    if (ringGeneration == generation) stopRingingLocked(audioManager)
                }
            }, RING_DURATION_MS)
        }
        return CommandResult.ok("Phone is ringing for ${RING_DURATION_MS / 1000} seconds")
    }

    private fun stopRingingLocked(audioManager: AudioManager) {
        activePlayer?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        activePlayer = null
        previousAlarmVolume?.let {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
            } catch (_: SecurityException) {
            }
        }
        previousAlarmVolume = null
    }

    private fun vibrate(pattern: LongArray, totalMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            Handler(Looper.getMainLooper()).postDelayed({ vibrator.cancel() }, totalMs)
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    // ── report_location ──────────────────────────────────────────────────────

    private fun reportLocation(): CommandResult {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            return CommandResult.failed("Location permission is not granted on the phone")
        }
        val location: Location? = try {
            val lm = context.getSystemService(LocationManager::class.java)
            lm.getProviders(true)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
        location ?: return CommandResult.failed("No recent location is available on the phone")
        return CommandResult.ok(
            "Last known location",
            mapOf(
                "lat" to location.latitude,
                "lng" to location.longitude,
                "accuracyM" to location.accuracy.toDouble(),
                "capturedAt" to Instant.ofEpochMilli(location.time).toString()
            )
        )
    }

    // ── show_message ─────────────────────────────────────────────────────────

    private fun showMessage(text: String): CommandResult {
        // Kept until MessageActivity displays it, so Home can show it if the
        // background activity start below is blocked by the OS.
        appPrefs.pendingCaretakerMessage = text
        return try {
            context.startActivity(
                Intent(context, MessageActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            CommandResult.ok("Message shown on the phone")
        } catch (e: Exception) {
            Log.e(TAG, "Could not open message screen", e)
            CommandResult.ok("Message will show when the phone is next used")
        }
    }

    // ── set_volume ───────────────────────────────────────────────────────────

    private fun setVolume(level: Int): CommandResult {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val failures = mutableListOf<String>()
        for ((stream, name) in listOf(AudioManager.STREAM_RING to "ring", AudioManager.STREAM_MUSIC to "media")) {
            try {
                val max = audioManager.getStreamMaxVolume(stream)
                audioManager.setStreamVolume(stream, (max * level / 100f).roundToInt(), 0)
            } catch (e: SecurityException) {
                // Changing ring volume to 0 needs Do Not Disturb access on some devices.
                failures += name
            }
        }
        return if (failures.isEmpty()) {
            CommandResult.ok("Volume set to $level%")
        } else {
            CommandResult.failed("Could not change ${failures.joinToString(" and ")} volume (Do Not Disturb)")
        }
    }

    companion object {
        private const val TAG = "CommandExecution"
        private const val STATUS_RUNNING = "running"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_FAILED = "failed"
        private const val RING_DURATION_MS = 30_000L

        private val ringLock = Any()
        private var activePlayer: MediaPlayer? = null
        private var previousAlarmVolume: Int? = null
        private var ringGeneration = 0
    }
}
