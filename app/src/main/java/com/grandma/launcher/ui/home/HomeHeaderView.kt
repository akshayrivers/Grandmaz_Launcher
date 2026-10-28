package com.grandma.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.grandma.launcher.R
import com.grandma.launcher.data.WeatherData
import com.grandma.launcher.data.WeatherRepository
import com.grandma.launcher.ui.alarm.AlarmActivity
import com.grandma.launcher.ui.views.AnalogClockView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom header compound view that combines the Analog Clock (left)
 * and the Date + Weather Card (right) into a unified senior-friendly zone.
 * Tapping the clock opens the alarm setter (hands for the time, sun and moon
 * for AM/PM). A long press is for the helper.
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
    var onClockLongPress: (() -> Unit)? = null
    var onWeatherClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        // Clock on left side (scaled down from 180dp to 130dp to fit side-by-side layouts)
        val clockSize = resources.getDimensionPixelSize(R.dimen.clock_diameter) - resources.getDimensionPixelSize(R.dimen.space_xxl)
        clockView = AnalogClockView(context).apply {
            layoutParams = LayoutParams(clockSize, clockSize).apply {
                gravity = Gravity.CENTER
            }
            contentDescription = context.getString(R.string.home_clock)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener { openAlarmSetter(context) }
            setOnLongClickListener {
                onClockLongPress?.invoke()
                true
            }
            // The home page scrolls. A long press is the helper's way into this phone's
            // settings, so the scroll view must not steal that hold.
            setOnTouchListener { view, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                false
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
            contentDescription = context.getString(R.string.nav_weather)
            setOnClickListener { onWeatherClick?.invoke() }
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
        weatherCard.bind(weatherRepo.getCurrentWeather())
    }

    fun showWeather(data: WeatherData) {
        weatherCard.bind(data)
    }

    companion object {
        /** Opens the alarm list with the hand / sun / moon setter already up. */
        fun openAlarmSetter(context: Context) {
            context.startActivity(
                Intent(context, AlarmActivity::class.java)
                    .putExtra(AlarmActivity.EXTRA_OPEN_SETTER, true)
            )
        }
    }
}
