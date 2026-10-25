package com.grandma.launcher.ui.home

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.grandma.launcher.R
import com.grandma.launcher.data.WeatherData
import com.grandma.launcher.ui.views.WeatherIconView

/**
 * A senior-friendly card view displaying weather information (large clean icon + temperature).
 * Assembled programmatically to avoid complex XML sync constraints.
 */
class WeatherCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val iconView: WeatherIconView
    private val tempText: TextView
    private val locationText: TextView

    init {
        radius = resources.getDimension(R.dimen.control_corner_radius)
        cardElevation = 0f
        useCompatPadding = false
        setCardBackgroundColor(ContextCompat.getColor(context, R.color.color_bg_card))
        strokeWidth = resources.getDimensionPixelSize(R.dimen.control_border_width)
        strokeColor = ContextCompat.getColor(context, R.color.color_control_border)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.space_md)
            setPadding(padding, padding, padding, padding)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val iconSize = resources.getDimensionPixelSize(R.dimen.contact_card_photo_size_contacts)
        iconView = WeatherIconView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.space_md)
            }
        }
        rootLayout.addView(iconView)

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tempText = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_Grandma_Heading)
            textSize = 28f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        textLayout.addView(tempText)

        locationText = TextView(context).apply {
            setTextAppearance(R.style.TextAppearance_Grandma_LabelSmall)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        textLayout.addView(locationText)

        rootLayout.addView(textLayout)
        addView(rootLayout)
    }

    fun bind(weather: WeatherData) {
        iconView.setCondition(weather.condition)
        if (!weather.known) {
            tempText.text = context.getString(R.string.weather_unavailable)
            locationText.text = ""
            return
        }
        tempText.text = "${weather.temperatureCelsius}°"
        locationText.text = weather.locationName
    }
}
