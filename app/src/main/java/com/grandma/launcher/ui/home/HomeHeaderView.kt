package com.grandma.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.grandma.launcher.R
import com.grandma.launcher.data.WeatherRepository
import com.grandma.launcher.ui.alarm.AlarmActivity
import com.grandma.launcher.ui.views.AnalogClockView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom header compound view that combines the Analog Clock (left)
 * and the Date + Weather Card (right) into a unified senior-friendly zone.
 * Tapping the clock opens the Alarm management screen.
 */
class HomeHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val clockView: AnalogClockView
    private val dateText: TextView
    private val weatherCard: WeatherCardView
    private val weatherRepo = WeatherRepository(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        // Clock on left side (scaled down from 180dp to 130dp to fit side-by-side layouts)
        val clockSize = resources.getDimensionPixelSize(R.dimen.clock_diameter) - resources.getDimensionPixelSize(R.dimen.space_xxl)
        clockView = AnalogClockView(context).apply {
            layoutParams = LayoutParams(clockSize, clockSize).apply {
                gravity = Gravity.CENTER
            }
            // Intuitive gesture: tapping the clock face launches the Alarms
            setOnClickListener {
                context.startActivity(Intent(context, AlarmActivity::class.java))
            }
        }
        addView(clockView)

        // Horizontal spacing spacer
        val spacer = View(context).apply {
            layoutParams = LayoutParams(resources.getDimensionPixelSize(R.dimen.space_md), 1)
        }
        addView(spacer)

        // Info layout on right (Date text + Weather Card)
        val rightLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.START
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            }
        }

        dateText = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_Grandma_LabelMedium)
            setTextColor(context.getColor(R.color.color_text_primary))
            textSize = 16f
            setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.space_sm))
        }
        rightLayout.addView(dateText)

        weatherCard = WeatherCardView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val weatherPackages = listOf(
                    "com.miui.weather2", // Xiaomi
                    "com.sec.android.app.weather", // Samsung
                    "com.samsung.android.weather", // Samsung
                    "com.coloros.weather", // Oppo
                    "com.huawei.android.totemweather", // Huawei
                    "com.htc.Weather" // HTC
                )
                var launched = false
                for (pkg in weatherPackages) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                            launched = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (!launched) {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"))
                    try {
                        context.startActivity(webIntent)
                    } catch (_: Exception) {}
                }
            }
        }
        rightLayout.addView(weatherCard)

        addView(rightLayout)

        updateDate()
        updateWeather()
    }

    fun updateDate() {
        val formatter = SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH)
        dateText.text = formatter.format(Date())
    }

    fun updateWeather() {
        val weather = weatherRepo.getCurrentWeather()
        weatherCard.bind(weather)
    }
}
