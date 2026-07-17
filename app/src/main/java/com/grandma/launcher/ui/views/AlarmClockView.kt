package com.grandma.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A custom view displaying a static analog clock face representing an alarm's set time.
 * Border lights up in active green if enabled, or dull gray if disabled,
 * giving instant wordless feedback.
 */
class AlarmClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var hour = 7
    private var minute = 0
    private var isAlarmEnabled = true

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_face)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_border)
        style = Paint.Style.STROKE
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_hand_hour)
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_hand_minute)
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_tick_major)
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    fun setTime(h: Int, m: Int, enabled: Boolean) {
        hour = h
        minute = m
        isAlarmEnabled = enabled
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 160
        val w = resolveSize(desiredSize, widthMeasureSpec)
        val h = resolveSize(desiredSize, heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val stroke = if (isAlarmEnabled) 10f else 6f
        val radius = (min(w, h) / 2f) - stroke / 2f

        borderPaint.color = if (isAlarmEnabled) {
            ContextCompat.getColor(context, R.color.color_accent_green)
        } else {
            Color.parseColor("#BDC3C7")
        }
        borderPaint.strokeWidth = stroke

        canvas.drawCircle(cx, cy, radius, facePaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Draw 4 main ticks (12, 3, 6, 9)
        val tickOuter = radius - borderPaint.strokeWidth / 2f
        val tickInner = tickOuter - radius * 0.18f
        for (i in 0 until 4) {
            val angle = Math.toRadians((i * 90.0) - 90.0)
            canvas.drawLine(
                (cx + cos(angle) * tickInner).toFloat(),
                (cy + sin(angle) * tickInner).toFloat(),
                (cx + cos(angle) * tickOuter).toFloat(),
                (cy + sin(angle) * tickOuter).toFloat(),
                tickPaint
            )
        }

        // Draw hands (12h conversion)
        val h12 = hour % 12
        val hourAngle = Math.toRadians((h12 * 30.0 + minute * 0.5) - 90.0)
        val minuteAngle = Math.toRadians((minute * 6.0) - 90.0)

        val hourLen = radius * 0.52f
        val minuteLen = radius * 0.76f

        canvas.drawLine(cx, cy, (cx + cos(hourAngle) * hourLen).toFloat(), (cy + sin(hourAngle) * hourLen).toFloat(), hourHandPaint)
        canvas.drawLine(cx, cy, (cx + cos(minuteAngle) * minuteLen).toFloat(), (cy + sin(minuteAngle) * minuteLen).toFloat(), minuteHandPaint)

        canvas.drawCircle(cx, cy, radius * 0.08f, hourHandPaint)
    }
}
