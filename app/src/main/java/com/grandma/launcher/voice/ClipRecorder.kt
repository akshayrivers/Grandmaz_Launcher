package com.grandma.launcher.voice

import android.media.MediaRecorder
import android.os.Build
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.network.DeviceTokenProvider
import org.json.JSONObject
import java.io.File

/** Records up to 30 seconds and uploads the clip. The file stays on disk for playback. */
class ClipRecorder(
    private val output: File,
    private val tokens: DeviceTokenProvider
) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    @Suppress("DEPRECATION")
    fun start() {
        output.parentFile?.mkdirs()
        val next = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder()
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        next.setAudioSource(MediaRecorder.AudioSource.MIC)
        next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        next.setMaxDuration(30_000)
        next.setOutputFile(output.absolutePath)
        next.prepare()
        next.start()
        startedAt = System.currentTimeMillis()
        recorder = next
    }

    suspend fun stopAndUpload(): ApiClient.Result<String> {
        val rec = recorder
        recorder = null
        try {
            rec?.stop()
        } catch (_: RuntimeException) {
            output.delete()
            return ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Recording was too short")
        } finally {
            rec?.release()
        }
        val duration = (System.currentTimeMillis() - startedAt).toInt().coerceIn(1, 30_000)
        val bytes = output.readBytes()
        val body = JSONObject()
            .put("kind", "audio")
            .put("contentType", "audio/mp4")
            .put("dataBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .put("durationMs", duration)
        return when (val result = ApiClient.deviceRequest("POST", "api/device/media", body, tokens)) {
            is ApiClient.Result.Error -> result
            is ApiClient.Result.Success -> {
                val id = (result.data as? JSONObject)?.optString("assetId").orEmpty()
                if (id.isBlank()) {
                    ApiClient.Result.Error(ApiClient.INVALID_RESPONSE, "Upload returned no asset")
                } else {
                    val stored = File(output.parentFile, "$id.m4a")
                    if (output.absolutePath != stored.absolutePath) output.renameTo(stored)
                    ApiClient.Result.Success(id)
                }
            }
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        recorder?.release()
        recorder = null
        output.delete()
    }
}
