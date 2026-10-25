package com.grandma.launcher.ui.caretaker

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.ActivityCaretakerConfigBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.CaretakerInvitationRepository
import com.grandma.launcher.remote.DeviceConfigRepository
import com.grandma.launcher.remote.DeviceProfileRepository
import com.grandma.launcher.remote.DeviceSyncManager
import kotlinx.coroutines.launch

/**
 * Helper-facing editor for the weather city. Saving marks the device config dirty and
 * asks sync to PUT it with the current version. Grandma does not use this screen.
 */
class CaretakerConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaretakerConfigBinding
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityCaretakerConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences(this)

        binding.etWeatherCity.setText(prefs.weatherCity)
        binding.tvConfigStatus.text = getString(R.string.caretaker_config_version, prefs.configVersion)
        binding.btnBack.setOnClickListener { finish() }
        CaretakerFabHelper.attach(this, binding.fabCaretaker, binding.btnBack)
        binding.btnSaveConfig.setOnClickListener { save() }
        binding.btnOverlayPermission.setOnClickListener { CaretakerFabHelper.openOverlaySettings(this) }
        binding.btnAddCaretaker.setOnClickListener { inviteCaretaker() }
        loadCaretakers()

        val horizontal = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.caretakerConfigRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(horizontal, bars.top, horizontal, bars.bottom)
            insets
        }
    }

    private fun loadCaretakers() {
        lifecycleScope.launch {
            when (val result = DeviceProfileRepository(this@CaretakerConfigActivity).linkedCaretakers()) {
                is ApiClient.Result.Success -> {
                    val people = result.data
                    binding.tvCaretakers.text = if (people.isEmpty()) {
                        getString(R.string.caretaker_list_empty)
                    } else {
                        people.joinToString("\n") { getString(R.string.caretaker_list_line, it.email, it.role) }
                    }
                }
                is ApiClient.Result.Error -> binding.tvCaretakers.text = getString(R.string.caretaker_list_empty)
            }
        }
    }

    private fun inviteCaretaker() {
        val email = binding.etCaretakerEmail.text?.toString()?.trim().orEmpty()
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            binding.tvInviteStatus.text = getString(R.string.setup_email_invalid)
            return
        }
        binding.btnAddCaretaker.isEnabled = false
        lifecycleScope.launch {
            when (val result = CaretakerInvitationRepository(this@CaretakerConfigActivity).createInvitation(email)) {
                is ApiClient.Result.Success -> {
                    val invite = result.data
                    binding.etCaretakerEmail.text?.clear()
                    binding.tvInviteStatus.text = if (invite.acceptPath.isNotBlank()) {
                        getString(R.string.caretaker_invite_sent, invite.pairingCode, invite.acceptPath)
                    } else {
                        getString(R.string.caretaker_invite_emailed, invite.pairingCode)
                    }
                }
                is ApiClient.Result.Error -> {
                    binding.tvInviteStatus.text = if (result.code == CaretakerInvitationRepository.CODE_ALREADY_LINKED) {
                        getString(R.string.caretaker_already_linked)
                    } else {
                        getString(R.string.caretaker_invite_failed)
                    }
                }
            }
            binding.btnAddCaretaker.isEnabled = true
        }
    }

    private fun save() {
        val city = binding.etWeatherCity.text?.toString()?.trim().orEmpty()
        val repo = DeviceConfigRepository(this)
        repo.setWeatherCity(city)
        binding.btnSaveConfig.isEnabled = false
        lifecycleScope.launch {
            when (val result = repo.sync()) {
                is ApiClient.Result.Success -> {
                    DeviceSyncManager.getInstance(this@CaretakerConfigActivity).requestSyncNow(force = true)
                    finish()
                }
                is ApiClient.Result.Error -> {
                    binding.btnSaveConfig.isEnabled = true
                    val text = if (result.code == "CONFIG_VERSION_CONFLICT") {
                        getString(R.string.caretaker_config_conflict)
                    } else {
                        getString(R.string.caretaker_config_save_failed)
                    }
                    Toast.makeText(this@CaretakerConfigActivity, text, Toast.LENGTH_LONG).show()
                    binding.tvConfigStatus.text = text
                }
            }
        }
    }
}
