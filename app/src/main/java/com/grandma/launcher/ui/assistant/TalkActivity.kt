package com.grandma.launcher.ui.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.databinding.ActivityTalkBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.AssistantRepository
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper
import com.grandma.launcher.ui.common.InactivityReturn
import com.grandma.launcher.voice.VoiceManager
import com.grandma.launcher.voice.VoiceSlots
import kotlinx.coroutines.launch

/**
 * Hold the microphone, then let go. The phone recognizes speech. The words go to
 * Gemini with a short-lived token from the backend. There is no API key in the app,
 * and this screen does not open the Gemini app.
 */
class TalkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTalkBinding
    private lateinit var inactivity: InactivityReturn
    private val voice by lazy { VoiceManager(this) }
    private val assistant by lazy { AssistantRepository(this) }
    private var recognizer: SpeechRecognizer? = null
    private var heard = ""
    private var fingerDown = false
    private var listening = false
    private var utteranceDone = false
    private var delivered = false
    private var asking = false
    private var askedForMic = false

    private val giveUp = Runnable {
        if (!utteranceDone && !fingerDown) {
            utteranceDone = true
            deliver()
        }
    }

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) pulse()
    }

    private fun listener(active: SpeechRecognizer) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (recognizer !== active) return
            finishUtterance()
        }

        override fun onResults(results: Bundle?) {
            if (recognizer !== active) return
            val text = best(results)
            if (text.isNotBlank()) heard = text
            finishUtterance()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (recognizer !== active) return
            val text = best(partialResults)
            if (text.isNotBlank()) heard = text
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityTalkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inactivity = InactivityReturn(this, binding.talkRoot)

        ViewCompat.setOnApplyWindowInsetsListener(binding.talkRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val margin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
            val fabBottom = resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_bottom)
            val clearance = (120 * resources.displayMetrics.density).toInt()
            binding.talkRoot.getChildAt(0).setPadding(margin, margin + bars.top, margin, clearance + bars.bottom)
            listOf(binding.fabCaretaker, binding.btnHome).forEach { button ->
                val params = button.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = fabBottom + bars.bottom
                button.layoutParams = params
            }
            insets
        }

        binding.btnHome.setOnClickListener {
            voice.speakSlot(VoiceSlots.HOME, getString(R.string.spoken_home))
            finish()
        }
        CaretakerFabHelper.attach(this, binding.fabCaretaker, binding.btnHome)
        binding.btnTalk.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> beginHold()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endHold()
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        CaretakerFabHelper.syncOverlay(this, binding.fabCaretaker, binding.btnHome)
        inactivity.arm()
    }

    override fun onPause() {
        inactivity.stop()
        cancelListen()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.actionMasked == MotionEvent.ACTION_DOWN) inactivity.arm()
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        binding.talkRoot.removeCallbacks(giveUp)
        cancelListen()
        voice.shutdown()
        super.onDestroy()
    }

    private fun beginHold() {
        if (asking) return
        if (!hasMicPermission()) {
            if (!askedForMic) {
                askedForMic = true
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                pulse()
            }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showUnavailable()
            return
        }
        cancelListen()
        fingerDown = true
        listening = true
        utteranceDone = false
        delivered = false
        heard = ""
        binding.tvTalkStatus.setText(R.string.talk_hold)
        binding.btnTalk.setBackgroundColor(Color.WHITE)
        binding.btnTalk.imageTintList = ColorStateList.valueOf(Color.BLACK)
        pulse()
        val next = try {
            SpeechRecognizer.createSpeechRecognizer(this)
        } catch (_: Exception) {
            listening = false
            showIdleButton()
            showUnavailable()
            return
        }
        recognizer = next
        next.setRecognitionListener(listener(next))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            next.startListening(intent)
        } catch (_: Exception) {
            listening = false
            showIdleButton()
            showUnavailable()
        }
    }

    private fun endHold() {
        if (!listening && !fingerDown) return
        fingerDown = false
        listening = false
        showIdleButton()
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        if (utteranceDone) {
            deliver()
        } else {
            binding.tvTalkStatus.setText(R.string.talk_checking)
            binding.talkRoot.removeCallbacks(giveUp)
            binding.talkRoot.postDelayed(giveUp, LISTEN_GIVE_UP_MS)
        }
    }

    private fun finishUtterance() {
        if (utteranceDone) return
        utteranceDone = true
        if (!fingerDown) deliver()
    }

    private fun deliver() {
        if (delivered || asking) return
        delivered = true
        binding.talkRoot.removeCallbacks(giveUp)
        val text = heard.trim()
        releaseRecognizer()
        if (text.isEmpty()) {
            binding.tvTalkStatus.setText(R.string.talk_hold)
            return
        }
        asking = true
        binding.tvTalkStatus.setText(R.string.talk_checking)
        lifecycleScope.launch {
            val result = assistant.ask(text)
            asking = false
            if (isDestroyed) return@launch
            when (result) {
                is ApiClient.Result.Success -> {
                    binding.tvTalkStatus.setText(R.string.talk_hold)
                    voice.speak(result.data, null)
                }
                is ApiClient.Result.Error -> {
                    if (result.code != "EMPTY") {
                        Log.e(TAG, "Ask failed: ${result.statusCode} ${result.code}")
                    }
                    showUnavailable()
                }
            }
        }
    }

    private fun cancelListen() {
        fingerDown = false
        listening = false
        utteranceDone = true
        delivered = true
        heard = ""
        binding.talkRoot.removeCallbacks(giveUp)
        showIdleButton()
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        val current = recognizer ?: return
        recognizer = null
        try {
            current.cancel()
        } catch (_: Exception) {
        }
        try {
            current.destroy()
        } catch (_: Exception) {
        }
    }

    private fun showIdleButton() {
        binding.btnTalk.setBackgroundResource(R.drawable.bg_control)
        binding.btnTalk.imageTintList = null
    }

    private fun showUnavailable() {
        binding.tvTalkStatus.setText(R.string.talk_unavailable)
        voice.speak(getString(R.string.talk_unavailable_spoken), null)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun best(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()

    private fun pulse() {
        try {
            getSystemService(Vibrator::class.java)?.vibrate(
                VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private companion object {
        const val TAG = "TalkActivity"
        const val LISTEN_GIVE_UP_MS = 2_500L
    }
}
