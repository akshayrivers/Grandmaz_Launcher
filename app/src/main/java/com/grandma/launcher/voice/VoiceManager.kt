package com.grandma.launcher.voice

import android.content.Context
import android.media.AudioAttributes
import com.grandma.launcher.remote.DeviceConfigRepository
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.getSystemService
import java.io.File
import java.util.Locale

/**
 * Speaks a name or button label. A recorded clip plays first. Otherwise Hindi,
 * then English. A vibration is the last fallback. A new request replaces whatever
 * is still playing. Dogri only exists as a recorded clip.
 */
interface Speaker {
    fun speak(text: String, clipId: String? = null)
    fun speakSlot(slot: String, text: String)
}

class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private var player: MediaPlayer? = null
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.setAudioAttributes(speechAttributes())
        }
        pending?.let { speakNow(it) }
        pending = null
    }

    fun speakSlot(slot: String, text: String) {
        val clip = try {
            DeviceConfigRepository(appContext).clipForSlot(slot)
        } catch (_: Exception) {
            null
        }
        speak(text, clip)
    }

    fun speak(text: String, clipId: String? = null) {
        stopSpeaking()
        val clip = clipId?.let { clipFile(it) }?.takeIf { it.exists() }
        if (clip != null && play(clip)) return
        if (!ready) {
            pending = text
            return
        }
        speakNow(text)
    }

    fun clipFile(assetId: String): File = File(appContext.filesDir, "voice/$assetId.m4a")

    fun shutdown() {
        stopSpeaking()
        tts.shutdown()
        ready = false
    }

    private fun speakNow(text: String) {
        val hindi = tts.setLanguage(Locale.forLanguageTag("hi-IN"))
        val language = if (hindi >= TextToSpeech.LANG_AVAILABLE) Locale.forLanguageTag("hi-IN") else Locale.ENGLISH
        if (tts.setLanguage(language) < TextToSpeech.LANG_AVAILABLE) {
            vibrate()
            return
        }
        tts.setSpeechRate(0.9f)
        val spoken = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "grandma-voice")
        if (spoken == TextToSpeech.ERROR) vibrate()
    }

    private fun play(file: File): Boolean {
        val next = MediaPlayer()
        return try {
            next.setAudioAttributes(speechAttributes())
            next.setDataSource(file.absolutePath)
            next.setOnCompletionListener { finished ->
                if (player === finished) player = null
                finished.release()
            }
            next.prepare()
            next.start()
            player = next
            true
        } catch (e: Exception) {
            Log.e(TAG, "Clip playback failed", e)
            next.release()
            false
        }
    }

    private fun stopSpeaking() {
        pending = null
        if (ready) tts.stop()
        player?.let { current ->
            player = null
            try {
                current.setOnCompletionListener(null)
                if (current.isPlaying) current.stop()
            } catch (_: Exception) {
            }
            current.release()
        }
    }

    private fun speechAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun vibrate() {
        appContext.getSystemService<Vibrator>()?.vibrate(
            VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    companion object {
        private const val TAG = "VoiceManager"
    }
}
