package com.grandma.launcher.ui.message

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.ActivityMessageBinding
import com.grandma.launcher.remote.MediaRepository
import com.grandma.launcher.ui.home.HomeActivity
import com.grandma.launcher.voice.VoiceManager
import kotlinx.coroutines.launch
import com.grandma.launcher.voice.VoiceSlots

/**
 * Full-screen message from a caretaker (show_message command). Grandma cannot read, so
 * a recorded clip plays first and the words are spoken otherwise. The text is for anyone nearby.
 * The house goes home. Inactivity also closes the screen.
 */
class MessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessageBinding
    private lateinit var appPrefs: AppPreferences
    private val voice by lazy { VoiceManager(this) }
    private var message = ""
    private var clipId: String? = null

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
            voice.speakSlot(VoiceSlots.HOME, getString(R.string.spoken_home))
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }
        binding.btnSpeakAgain.setOnClickListener {
            pulse()
            speak()
        }

        message = savedInstanceState?.getString(STATE_MESSAGE).orEmpty()
        clipId = savedInstanceState?.getString(STATE_CLIP)
        loadMessage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_MESSAGE, message)
        outState.putString(STATE_CLIP, clipId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadMessage()
    }

    private fun loadMessage() {
        val pending = appPrefs.pendingCaretakerMessage
        if (pending.isNotBlank()) {
            message = pending
            clipId = appPrefs.pendingCaretakerMessageClip.takeIf { it.isNotBlank() }
            appPrefs.clearPendingCaretakerMessage()
        }
        if (message.isBlank()) {
            finish()
            return
        }
        binding.tvMessage.text = message
        pulse()
        speak()
        restartAutoClose()
    }

    private fun speak() {
        if (message.isBlank()) return
        restartAutoClose()
        val id = clipId
        if (id.isNullOrBlank()) {
            voice.speak(message, null)
            return
        }
        val local = java.io.File(filesDir, "voice/$id.m4a")
        if (local.exists()) {
            voice.speak(message, id)
            return
        }
        lifecycleScope.launch {
            MediaRepository(this@MessageActivity).ensureClip(id)
            voice.speak(message, id)
            restartAutoClose()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) restartAutoClose()
        return super.dispatchTouchEvent(ev)
    }

    private fun restartAutoClose() {
        binding.root.removeCallbacks(autoCloseRunnable)
        binding.root.postDelayed(autoCloseRunnable, AUTO_CLOSE_MS)
    }

    private fun pulse() {
        try {
            getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(autoCloseRunnable)
        voice.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MessageActivity"
        const val STATE_MESSAGE = "message"
        const val STATE_CLIP = "clip"
        const val AUTO_CLOSE_MS = 2 * 60 * 1000L
    }
}
