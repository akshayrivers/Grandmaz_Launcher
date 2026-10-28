package com.grandma.launcher.ui.caretaker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
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
import com.grandma.launcher.remote.DeviceLifecycleRepository
import com.grandma.launcher.remote.DeviceProfileRepository
import com.grandma.launcher.remote.DeviceSyncManager
import kotlinx.coroutines.launch

/**
 * Helper screen opened after a long press on the home clock and the caretaker PIN.
 * Weather city is saved on this phone. Another caretaker can be invited from here
 * even when the phone is offline — the form stays, and the screen says so.
 * Grandma does not use this screen.
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
        CaretakerFabHelper.attach(this, binding.fabCaretaker)
        binding.btnSaveConfig.setOnClickListener { save() }
        binding.btnOverlayPermission.setOnClickListener { CaretakerFabHelper.openOverlaySettings(this) }
        binding.btnAddCaretaker.setOnClickListener { inviteCaretaker() }
        binding.btnUninstall.setOnClickListener { reportUninstall() }
        showCaretakers(prefs.linkedCaretakers().map { (email, role) ->
            DeviceProfileRepository.LinkedCaretaker(email, role)
        })

        val horizontal = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.caretakerConfigRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(horizontal, bars.top, horizontal, bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        CaretakerFabHelper.syncOverlay(this, binding.fabCaretaker)
        if (prefs.uninstallNoticePending) {
            prefs.uninstallNoticePending = false
            binding.btnUninstall.isEnabled = true
            lifecycleScope.launch {
                DeviceLifecycleRepository(this@CaretakerConfigActivity).reportStillInstalled()
            }
        }
        loadCaretakers()
    }

    private fun showCaretakers(people: List<DeviceProfileRepository.LinkedCaretaker>) {
        binding.tvCaretakers.text = if (people.isEmpty()) {
            getString(R.string.caretaker_list_empty)
        } else {
            people.joinToString("\n") { getString(R.string.caretaker_list_line, it.email, it.role) }
        }
    }

    private fun loadCaretakers() {
        showCaretakers(prefs.linkedCaretakers().map { (email, role) ->
            DeviceProfileRepository.LinkedCaretaker(email, role)
        })
        if (!internetConnected(this)) {
            showOffline()
            return
        }
        binding.tvOffline.visibility = View.GONE
        lifecycleScope.launch {
            when (val result = DeviceProfileRepository(this@CaretakerConfigActivity).linkedCaretakers()) {
                is ApiClient.Result.Success -> showCaretakers(result.data)
                is ApiClient.Result.Error -> if (result.isNetworkError) showOffline()
            }
        }
    }

    private fun showOffline() {
        binding.tvOffline.visibility = View.VISIBLE
        binding.tvOffline.setText(R.string.caretaker_offline)
    }

    private fun inviteCaretaker() {
        val email = binding.etCaretakerEmail.text?.toString()?.trim().orEmpty()
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            binding.tvInviteStatus.setText(R.string.setup_email_invalid)
            return
        }
        if (!internetConnected(this)) {
            showOffline()
            binding.tvInviteStatus.setText(R.string.caretaker_offline)
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
                    if (result.isNetworkError) showOffline()
                    binding.tvInviteStatus.setText(caretakerInviteMessage(result))
                }
            }
            binding.btnAddCaretaker.isEnabled = true
        }
    }

    private fun reportUninstall() {
        if (!internetConnected(this)) {
            showOffline()
            binding.tvInviteStatus.setText(R.string.caretaker_offline)
            return
        }
        binding.btnUninstall.isEnabled = false
        lifecycleScope.launch {
            when (val result = DeviceLifecycleRepository(this@CaretakerConfigActivity).reportUninstalled()) {
                is ApiClient.Result.Success -> {
                    prefs.uninstallNoticePending = true
                    binding.tvInviteStatus.setText(R.string.caretaker_uninstalled)
                    try {
                        startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
                    } catch (_: ActivityNotFoundException) {
                        prefs.uninstallNoticePending = false
                        binding.btnUninstall.isEnabled = true
                        DeviceLifecycleRepository(this@CaretakerConfigActivity).reportStillInstalled()
                    }
                }
                is ApiClient.Result.Error -> {
                    binding.btnUninstall.isEnabled = true
                    if (result.isNetworkError) showOffline()
                    binding.tvInviteStatus.setText(
                        if (result.isNetworkError) R.string.caretaker_offline else R.string.caretaker_invite_failed
                    )
                }
            }
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
                    val text = if (result.isNetworkError) {
                        getString(R.string.caretaker_offline)
                    } else if (result.code == "CONFIG_VERSION_CONFLICT") {
                        getString(R.string.caretaker_config_conflict)
                    } else {
                        getString(R.string.caretaker_config_save_failed)
                    }
                    if (result.isNetworkError) showOffline()
                    Toast.makeText(this@CaretakerConfigActivity, text, Toast.LENGTH_LONG).show()
                    binding.tvConfigStatus.text = text
                }
            }
        }
    }

    companion object {
        /** Which sentence to show after an invitation call fails. */
        internal fun caretakerInviteMessage(error: ApiClient.Result.Error): Int = when {
            error.isNetworkError -> R.string.caretaker_offline
            error.code == CaretakerInvitationRepository.CODE_ALREADY_LINKED -> R.string.caretaker_already_linked
            else -> R.string.caretaker_invite_failed
        }

        internal fun internetConnected(context: Context): Boolean {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
