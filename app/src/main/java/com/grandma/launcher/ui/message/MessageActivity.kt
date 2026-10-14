package com.grandma.launcher.ui.message

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.ActivityMessageBinding
import java.util.Locale

/**
 * Full-screen message from a caretaker (show_message command). Grandma cannot read, so
 * the text is spoken aloud (Hindi, English fallback); the text is for anyone nearby.
 * One action: the big green button goes back home. Inactivity also returns home.
 */
class MessageActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMessageBinding
    private lateinit var appPrefs: AppPreferences
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var message = ""

    private val autoCloseRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        binding = ActivityMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        appPrefs = AppPreferences(this)

        val margin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.messageRootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(margin, margin + bars.top, margin, margin + bars.bottom)
            insets
        }

        binding.btnOk.setOnClickListener {
            vibrateTap()
            finish()
        }
        binding.btnSpeakAgain.setOnClickListener {
            vibrateTap()
            speak()
        }

        message = savedInstanceState?.getString(STATE_MESSAGE).orEmpty()
        loadMessage()
        tts = TextToSpeech(this, this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_MESSAGE, message)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        loadMessage()
        speak()
    }

    private fun loadMessage() {
        val pending = appPrefs.pendingCaretakerMessage
        if (pending.isNotBlank()) {
            message = pending
            appPrefs.pendingCaretakerMessage = ""
        }
        if (message.isBlank()) {
            finish()
            return
        }
        binding.tvMessage.text = message
        vibrateTap()
        restartAutoClose()
    }

    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TextToSpeech init failed: $status")
            return
        }
        val preferred = if (appPrefs.language == AppPreferences.LANGUAGE_ENGLISH) Locale.ENGLISH else HINDI
        val fallback = if (preferred == HINDI) Locale.ENGLISH else HINDI
        ttsReady = engine.setLanguage(preferred) >= TextToSpeech.LANG_AVAILABLE ||
            engine.setLanguage(fallback) >= TextToSpeech.LANG_AVAILABLE
        speak()
    }

    private fun speak() {
        if (!ttsReady || message.isBlank()) return
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        restartAutoClose()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) restartAutoClose()
        return super.dispatchTouchEvent(ev)
    }

    private fun restartAutoClose() {
        binding.root.removeCallbacks(autoCloseRunnable)
        binding.root.postDelayed(autoCloseRunnable, AUTO_CLOSE_MS)
    }

    private fun vibrateTap() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(autoCloseRunnable)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MessageActivity"
        const val UTTERANCE_ID = "caretaker_message"
        const val STATE_MESSAGE = "message"
        const val AUTO_CLOSE_MS = 2 * 60 * 1000L
        val HINDI: Locale = Locale("hi", "IN")
    }
}
