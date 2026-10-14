package com.grandma.launcher.ui.assistant

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.databinding.ActivityTalkBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceRegistrationRepository
import com.grandma.launcher.voice.VoiceManager
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Push-to-talk. Audio stays on the phone. The server only says whether a text
 * assistant session is available.
 */
class TalkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTalkBinding
    private val voice by lazy { VoiceManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityTalkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnHome.setOnClickListener { finish() }
        binding.btnTalk.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) ask()
            true
        }
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
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
