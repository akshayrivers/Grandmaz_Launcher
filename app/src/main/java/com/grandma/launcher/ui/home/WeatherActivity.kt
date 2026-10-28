package com.grandma.launcher.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.data.WeatherCondition
import com.grandma.launcher.data.WeatherData
import com.grandma.launcher.data.WeatherRepository
import com.grandma.launcher.databinding.ActivityWeatherBinding
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper
import com.grandma.launcher.ui.common.InactivityReturn
import com.grandma.launcher.voice.VoiceManager
import kotlinx.coroutines.launch

/** Full-screen weather. She hears the day. The house returns to Home. */
class WeatherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherBinding
    private lateinit var inactivity: InactivityReturn
    private val voice by lazy { VoiceManager(this) }
    private val repository by lazy { WeatherRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        inactivity = InactivityReturn(this, binding.weatherRoot)

        ViewCompat.setOnApplyWindowInsetsListener(binding.weatherRoot) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val margin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
            val fabBottom = resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_bottom)
            val clearance = (120 * resources.displayMetrics.density).toInt()
            binding.weatherContent.setPadding(margin, margin + bars.top, margin, clearance + bars.bottom)
            listOf(binding.fabCaretaker, binding.btnHouse).forEach { button ->
                val params = button.layoutParams as android.view.ViewGroup.MarginLayoutParams
                params.bottomMargin = fabBottom + bars.bottom
                button.layoutParams = params
            }
            insets
        }

        binding.btnHouse.setOnClickListener { finish() }
        CaretakerFabHelper.attach(this, binding.fabCaretaker, binding.btnHouse)
        show(repository.getCurrentWeather(), offerDeviceApp = false)
        loadWeather()
    }

    private fun loadWeather() {
        val prefs = AppPreferences(this)
        val needsLocation = prefs.weatherMode == "location" || prefs.weatherCity.isBlank()
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (needsLocation && !granted) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_REQUEST)
            return
        }
        lifecycleScope.launch { show(repository.refresh(), offerDeviceApp = true) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST) {
            lifecycleScope.launch { show(repository.refresh(), offerDeviceApp = true) }
        }
    }

    private var spokenPhrase: String? = null

    private fun show(data: WeatherData, offerDeviceApp: Boolean) {
        binding.weatherIcon.setCondition(data.condition)
        if (!data.known) {
            binding.tvTemperature.text = getString(R.string.weather_unavailable)
            val phrase = getString(R.string.spoken_weather_unavailable)
            if (phrase != spokenPhrase) {
                spokenPhrase = phrase
                voice.speak(phrase, null)
            }
            if (offerDeviceApp) openDeviceWeatherOrClock()
            return
        }
        binding.tvTemperature.text = getString(R.string.weather_temperature, data.temperatureCelsius)
        val phrase = spoken(data)
        if (phrase == spokenPhrase) return
        spokenPhrase = phrase
        voice.speak(phrase, null)
    }

    private fun openDeviceWeatherOrClock() {
        val weather = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_WEATHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(weather)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun spoken(data: WeatherData): String {
        val phrase = when (data.condition) {
            WeatherCondition.SUNNY -> R.string.weather_spoken_sunny
            WeatherCondition.CLOUDY -> R.string.weather_spoken_cloudy
            WeatherCondition.RAINY -> R.string.weather_spoken_rainy
            WeatherCondition.THUNDERSTORM -> R.string.weather_spoken_storm
            WeatherCondition.SNOWY -> R.string.weather_spoken_snowy
        }
        return getString(phrase, data.temperatureCelsius)
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

    companion object {
        private const val LOCATION_REQUEST = 41
        private val HOME_PACKAGES = setOf("com.miui.home", "com.android.launcher", "com.android.launcher3")
    }
}
