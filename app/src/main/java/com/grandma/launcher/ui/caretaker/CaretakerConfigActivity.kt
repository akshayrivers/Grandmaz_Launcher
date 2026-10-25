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
import com.grandma.launcher.remote.DeviceConfigRepository
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

        val horizontal = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.caretakerConfigRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(horizontal, bars.top, horizontal, bars.bottom)
            insets
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
