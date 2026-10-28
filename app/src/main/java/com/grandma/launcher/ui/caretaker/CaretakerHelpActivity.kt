package com.grandma.launcher.ui.caretaker

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.appScope
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.ActivityCaretakerHelpBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceProfileRepository
import com.grandma.launcher.remote.HelpRequestRepository
import com.grandma.launcher.ui.common.InactivityReturn
import com.grandma.launcher.ui.home.HomeActivity
import com.grandma.launcher.voice.VoiceManager
import com.grandma.launcher.voice.VoiceSlots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One picture. Tapping it tells every caretaker she needs help, then returns home.
 * The words under the picture name who is linked. The floating home is the way back.
 */
class CaretakerHelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaretakerHelpBinding
    private lateinit var prefs: AppPreferences
    private lateinit var inactivity: InactivityReturn
    private val voice by lazy { VoiceManager(this) }
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityCaretakerHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)
        inactivity = InactivityReturn(this, binding.caretakerHelpRootLayout)

        binding.btnSendHelp.setOnClickListener { sendHelpRequest() }
        binding.btnEditHome.setOnClickListener {
            CaretakerPinDialog.show(this) {
                startActivity(Intent(this, CaretakerConfigActivity::class.java))
            }
        }
        showPeople(prefs.linkedCaretakers())

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
        refreshPeople()
    }

    private fun showPeople(people: List<Pair<String, String>>) {
        binding.tvHelpPeople.text = if (people.isEmpty()) {
            getString(R.string.caretaker_list_empty)
        } else {
            getString(R.string.caretaker_help_people, people.joinToString("\n") { (email, role) ->
                getString(R.string.caretaker_list_line, email, role)
            })
        }
    }

    private fun refreshPeople() {
        showPeople(prefs.linkedCaretakers())
        val offline = !CaretakerConfigActivity.internetConnected(this)
        binding.tvOffline.visibility = if (offline) View.VISIBLE else View.GONE
        if (offline) return
        lifecycleScope.launch {
            when (val result = DeviceProfileRepository(this@CaretakerHelpActivity).linkedCaretakers()) {
                is ApiClient.Result.Success -> showPeople(result.data.map { it.email to it.role })
                is ApiClient.Result.Error -> if (result.isNetworkError) {
                    binding.tvOffline.visibility = View.VISIBLE
                }
            }
        }
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
        if (sending) return
        if (!CaretakerConfigActivity.internetConnected(this)) {
            binding.tvOffline.visibility = View.VISIBLE
            binding.tvHelpStatus.setText(R.string.caretaker_offline)
            pulse()
            voice.speak(getString(R.string.spoken_help_again), null)
            return
        }
        sending = true
        binding.btnSendHelp.isEnabled = false
        binding.tvHelpStatus.text = ""
        pulse()
        voice.speakSlot(VoiceSlots.HELP, getString(R.string.spoken_help))
        val helpRepo = HelpRequestRepository(applicationContext)
        appScope.launch {
            val result = helpRepo.createHelpRequest(
                title = "Help requested from launcher",
                description = getString(R.string.caretaker_help_note),
                type = HelpRequestRepository.HelpType.GENERAL
            )
            withContext(Dispatchers.Main) {
                sending = false
                if (!isDestroyed) binding.btnSendHelp.isEnabled = true
                when (result) {
                    is ApiClient.Result.Success -> goHome()
                    is ApiClient.Result.Error -> {
                        Log.e(TAG, "Help request failed: ${result.statusCode} ${result.code}")
                        if (!isDestroyed) {
                            if (result.isNetworkError) {
                                binding.tvOffline.visibility = View.VISIBLE
                                binding.tvHelpStatus.setText(R.string.caretaker_offline)
                            }
                            pulse()
                            voice.speak(getString(R.string.spoken_help_again), null)
                        }
                    }
                }
            }
        }
    }

    private fun pulse() {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun goHome() {
        inactivity.stop()
        val home = Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (isDestroyed) {
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applicationContext.startActivity(home)
            return
        }
        startActivity(home)
        finish()
    }

    companion object {
        private const val TAG = "CaretakerHelp"
    }
}
