package com.grandma.launcher.remote

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles fetching, executing, and reporting remote caretaker commands/tasks.
 */
class CommandExecutionManager(private val context: Context) {

    private val appPrefs = AppPreferences(context)
    private val stateSnapshotRepository = StateSnapshotRepository(context)

    suspend fun pollAndExecuteCommands(): Int = withContext(Dispatchers.IO) {
        val baseUrl = appPrefs.backendBaseUrl
        val deviceId = appPrefs.deviceId

        val endpoint = "api/commands/device/$deviceId?status=pending"
        val result = ApiClient.getJsonArray(baseUrl, endpoint)

        if (result !is ApiClient.Result.Success) {
            return@withContext 0
        }

        val tasksArray: JSONArray = result.data
        var executedCount = 0

        for (i in 0 until tasksArray.length()) {
            val taskObj = tasksArray.optJSONObject(i) ?: continue
            val taskId = taskObj.optString("id", "")
            val commandName = taskObj.optString("command", "")
            val payload = taskObj.optJSONObject("payload") ?: JSONObject()

            if (taskId.isBlank()) continue

            // 1. Mark task status = running
            updateTaskStatus(taskId, "running", null)

            // 2. Execute command
            val executionResult = executeCommand(commandName, payload)

            // 3. Mark task status = completed or failed
            val finalStatus = if (executionResult.optBoolean("success", true)) "completed" else "failed"
            updateTaskStatus(taskId, finalStatus, executionResult)
            executedCount++
        }

        executedCount
    }

    private suspend fun executeCommand(commandName: String, payload: JSONObject): JSONObject {
        val result = JSONObject()
        try {
            when (commandName.lowercase()) {
                "sync_state", "post_snapshot", "ping" -> {
                    val snapResult = stateSnapshotRepository.postSnapshot()
                    result.put("success", snapResult is ApiClient.Result.Success)
                    result.put("message", "State snapshot triggered successfully")
                }
                "update_emergency_number" -> {
                    val newNumber = payload.optString("emergencyNumber", payload.optString("number", ""))
                    if (newNumber.isNotBlank()) {
                        appPrefs.emergencyNumber = newNumber
                        result.put("success", true)
                        result.put("message", "Emergency number updated to $newNumber")
                    } else {
                        result.put("success", false)
                        result.put("message", "No emergency number provided in payload")
                    }
                }
                "update_caretaker" -> {
                    val newEmail = payload.optString("email", "")
                    val newName = payload.optString("name", "")
                    if (newEmail.isNotBlank()) {
                        appPrefs.caretakerEmail = newEmail
                    }
                    if (newName.isNotBlank()) {
                        appPrefs.caretakerName = newName
                    }
                    result.put("success", true)
                    result.put("message", "Caretaker info updated")
                }
                "vibrate", "alert" -> {
                    triggerVibration()
                    result.put("success", true)
                    result.put("message", "Vibration triggered on device")
                }
                else -> {
                    result.put("success", false)
                    result.put("message", "Unknown or unsupported command: $commandName")
                }
            }
        } catch (e: Exception) {
            result.put("success", false)
            result.put("error", e.localizedMessage ?: "Execution exception")
        }
        return result
    }

    private suspend fun updateTaskStatus(taskId: String, status: String, resultData: JSONObject?) {
        val baseUrl = appPrefs.backendBaseUrl
        val body = JSONObject().apply {
            put("status", status)
            if (resultData != null) {
                put("result", resultData)
            }
        }
        ApiClient.patchJson(baseUrl, "api/commands/$taskId/status", body)
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(1000)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
