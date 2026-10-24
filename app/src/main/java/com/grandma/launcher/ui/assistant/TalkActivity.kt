package com.grandma.launcher.ui.assistant

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.databinding.ActivityTalkBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceRegistrationRepository
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper
import com.grandma.launcher.ui.common.InactivityReturn
import com.grandma.launcher.voice.VoiceManager
import com.grandma.launcher.voice.VoiceSlots
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Push-to-talk. Audio stays on the phone. The server only says whether a text
 * assistant session is available.
 */
class TalkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTalkBinding
    private lateinit var inactivity: InactivityReturn
    private val voice by lazy { VoiceManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityTalkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inactivity = InactivityReturn(this, binding.talkRoot)
        val margin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.talkRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(margin, margin + bars.top, margin, margin + bars.bottom)
            insets
        }
        binding.btnHome.setOnClickListener {
            voice.speakSlot(VoiceSlots.HOME, getString(R.string.spoken_home))
            finish()
        }
        CaretakerFabHelper.attach(this, binding.fabCaretaker, binding.btnHome)
        binding.btnTalk.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> showHolding(true)
                MotionEvent.ACTION_UP -> {
                    showHolding(false)
                    view.performClick()
                    ask()
                }
                MotionEvent.ACTION_CANCEL -> showHolding(false)
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        inactivity.arm()
    }

    override fun onPause() {
        inactivity.stop()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) inactivity.arm()
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }

    private fun showHolding(holding: Boolean) {
        if (holding) {
            binding.btnTalk.setBackgroundColor(Color.WHITE)
            binding.btnTalk.imageTintList = ColorStateList.valueOf(Color.BLACK)
        } else {
            binding.btnTalk.setBackgroundResource(R.drawable.bg_control)
            binding.btnTalk.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.color_text_primary)
            )
        }
    }

    private fun ask() {
        binding.tvTalkStatus.setText(R.string.talk_checking)
        lifecycleScope.launch {
            when (val result = ApiClient.deviceRequest(
                "POST",
                "api/device/ai/session",
                JSONObject(),
                DeviceRegistrationRepository(this@TalkActivity)
            )) {
                is ApiClient.Result.Error -> {
                    binding.tvTalkStatus.setText(R.string.talk_unavailable)
                    voice.speak(getString(R.string.talk_unavailable_spoken), null)
                }
                is ApiClient.Result.Success -> {
                    val reply = (result.data as? JSONObject)?.optString("reply").orEmpty()
                    if (reply.isBlank() || reply == "null") {
                        binding.tvTalkStatus.setText(R.string.talk_unavailable)
                        voice.speak(getString(R.string.talk_unavailable_spoken), null)
                    } else {
                        binding.tvTalkStatus.text = reply
                        voice.speak(reply, null)
                    }
                }
            }
        }
    }
}
