package com.grandma.launcher.ui.caretaker

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.grandma.launcher.R
import com.grandma.launcher.appScope
import com.grandma.launcher.databinding.ActivityCaretakerHelpBinding
import com.grandma.launcher.remote.HelpRequestRepository
import com.grandma.launcher.ui.common.InactivityReturn
import com.grandma.launcher.ui.home.HomeActivity
import com.grandma.launcher.voice.VoiceManager
import com.grandma.launcher.voice.VoiceSlots
import kotlinx.coroutines.launch

/**
 * One picture. Tapping it tells every caretaker she needs help, then returns home.
 * The small words at the bottom open the helper's weather editor.
 */
class CaretakerHelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaretakerHelpBinding
    private lateinit var inactivity: InactivityReturn
    private val voice by lazy { VoiceManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityCaretakerHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inactivity = InactivityReturn(this, binding.caretakerHelpRootLayout)

        binding.btnBack.setOnClickListener {
            voice.speakSlot(VoiceSlots.HOME, getString(R.string.spoken_home))
            goHome()
        }
        binding.btnSendHelp.setOnClickListener { sendHelpRequest() }
        binding.btnEditHome.setOnClickListener {
            startActivity(Intent(this, CaretakerConfigActivity::class.java))
        }

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        val topMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_top)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_bottom)
        ViewCompat.setOnApplyWindowInsetsListener(binding.caretakerHelpRootLayout) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                horizontalMargin,
                topMargin + systemBars.top,
                horizontalMargin,
                bottomMargin + systemBars.bottom
            )
            insets
        }

        voice.speakSlot(VoiceSlots.HELP, getString(R.string.spoken_help))
    }

    override fun onResume() {
        super.onResume()
        inactivity.arm()
    }

    override fun onPause() {
        inactivity.stop()
        super.onPause()
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) inactivity.arm()
        return super.dispatchTouchEvent(ev)
    }

    private fun sendHelpRequest() {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        voice.speakSlot(VoiceSlots.HELP, getString(R.string.spoken_help))
        val helpRepo = HelpRequestRepository(applicationContext)
        appScope.launch {
            helpRepo.createHelpRequest(
                title = "Help requested from launcher",
                type = HelpRequestRepository.HelpType.GENERAL
            )
        }
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
