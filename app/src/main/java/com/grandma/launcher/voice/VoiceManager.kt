package com.grandma.launcher.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.getSystemService
import java.io.File
import java.util.Locale

/**
 * Speaks a name or button label: a recorded clip first, then Hindi TTS, then English,
 * then a vibration if no engine is available. Dogri is only a recorded clip.
 */
class VoiceManager(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        pending?.let { speakNow(it) }
        pending = null
    }

    fun speak(text: String, clipId: String? = null) {
        val clip = clipId?.let { clipFile(it) }
        if (clip != null && clip.exists()) {
            play(clip)
            return
        }
        if (!ready) {
            pending = text
            return
        }
        speakNow(text)
    }

    fun clipFile(assetId: String): File = File(appContext.filesDir, "voice/$assetId.m4a")

    fun shutdown() {
        tts.stop()
        tts.shutdown()
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

    private fun play(file: File) {
        try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clip playback failed", e)
            vibrate()
        }
    }

    private fun vibrate() {
        appContext.getSystemService<Vibrator>()?.vibrate(
            VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    companion object {
        private const val TAG = "VoiceManager"
    }
}
