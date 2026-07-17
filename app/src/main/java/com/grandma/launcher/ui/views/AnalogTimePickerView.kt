package com.grandma.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * An interactive, senior-friendly analog time picker custom view.
 * Enables elderly and non-literate users to set alarms by:
 * 1. Tapping Sun/Moon symbols to select AM/PM.
 * 2. Tapping the clock face to place the Hour or Minute hand.
 * 3. Vibrating on every tick/interaction for physical feedback.
 */
class AnalogTimePickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { HOUR, MINUTE }

    var currentHour = 7
        set(value) {
            field = if (value == 0) 12 else value
            invalidate()
        }
    var currentMinute = 0
        set(value) {
            field = (value / 5) * 5 // snap to 5-min intervals
            invalidate()
        }
    var isAm = true
        set(value) {
            field = value
            invalidate()
        }
    var selectionMode = Mode.HOUR
        set(value) {
            field = value
            invalidate()
        }

    var onTimeChanged: ((hour: Int, minute: Int, isAm: Boolean) -> Unit)? = null

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // ── Paints ───────────────────────────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_face)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_border)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val selectRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_accent_green)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val selectBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_accent_green_light)
        style = Paint.Style.FILL
    }

    private val handHourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_hand_hour)
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }

    private val handMinutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_accent_blue) // Blue minute hand
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_primary)
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val tabTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_primary)
        textSize = 44f
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    // Geometry parameters (calculated on size change)
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private var sunCx = 0f
    private var sunCy = 0f
    private var moonCx = 0f
    private var moonCy = 0f
    private val buttonRadius = 60f

    private val tabHourRect = RectF()
    private val tabMinuteRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wF = w.toFloat()
        val hF = h.toFloat()

        // Sun/Moon headers
        sunCx = wF * 0.3f
        sunCy = hF * 0.12f
        moonCx = wF * 0.7f
        moonCy = hF * 0.12f

        // Main clock dial placement (center of the view)
        cx = wF * 0.5f
        cy = hF * 0.52f
        radius = min(wF, hF) * 0.33f

        // Bottom selection tabs
        val tabW = wF * 0.4f
        val tabH = 90f
        val tabY = hF * 0.9f
        tabHourRect.set(wF * 0.1f, tabY - tabH / 2, wF * 0.1f + tabW, tabY + tabH / 2)
        tabMinuteRect.set(wF * 0.5f, tabY - tabH / 2, wF * 0.5f + tabW, tabY + tabH / 2)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 750 // nice large touch dial
        val w = resolveSize(desiredSize, widthMeasureSpec)
        val h = resolveSize(desiredSize, heightMeasureSpec)
        val size = min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f) return

        // 1. Draw AM (Sun) and PM (Moon) buttons
        drawAmPmButtons(canvas)

        // 2. Draw clock face and ticks
        drawClockDial(canvas)

        // 3. Draw hands representing currently selected time
        drawHands(canvas)

        // 4. Draw bottom mode selection tabs (HOUR vs MINUTE)
        drawSelectionTabs(canvas)
    }

    private fun drawAmPmButtons(canvas: Canvas) {
        // AM/Sun Button
        if (isAm) {
            canvas.drawCircle(sunCx, sunCy, buttonRadius, selectBgPaint)
            canvas.drawCircle(sunCx, sunCy, buttonRadius, selectRingPaint)
        } else {
            canvas.drawCircle(sunCx, sunCy, buttonRadius, bgPaint)
            canvas.drawCircle(sunCx, sunCy, buttonRadius, borderPaint)
        }
        // Sun drawing (Yellow circle + rays)
        val sunColor = Color.parseColor("#F5B041")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sunColor; style = Paint.Style.FILL }
        canvas.drawCircle(sunCx, sunCy, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            canvas.drawLine(
                (sunCx + cos(angle) * 22f).toFloat(), (sunCy + sin(angle) * 22f).toFloat(),
                (sunCx + cos(angle) * 32f).toFloat(), (sunCy + sin(angle) * 32f).toFloat(), paint
            )
        }

        // PM/Moon Button
        if (!isAm) {
            canvas.drawCircle(moonCx, moonCy, buttonRadius, selectBgPaint)
            canvas.drawCircle(moonCx, moonCy, buttonRadius, selectRingPaint)
        } else {
            canvas.drawCircle(moonCx, moonCy, buttonRadius, bgPaint)
            canvas.drawCircle(moonCx, moonCy, buttonRadius, borderPaint)
        }
        // Moon drawing (Crescent)
        val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7F8C8D")
            style = Paint.Style.FILL
        }
        val moonPath = Path().apply {
            addCircle(moonCx + 4f, moonCy, 20f, Path.Direction.CW)
        }
        val clipPath = Path().apply {
            addCircle(moonCx - 4f, moonCy, 18f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipOutPath(clipPath)
        canvas.drawPath(moonPath, moonPaint)
        canvas.restore()
    }

    private fun drawClockDial(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Draw numbers/ticks
        for (i in 1..12) {
            val angle = Math.toRadians((i * 30.0) - 90.0)
            val textDistance = radius * 0.78f
            val x = (cx + cos(angle) * textDistance).toFloat()
            val y = (cy + sin(angle) * textDistance).toFloat() + 14f // offset baseline centering

            if (selectionMode == Mode.HOUR) {
                canvas.drawText(i.toString(), x, y, textPaint)
            } else {
                val minVal = (i * 5) % 60
                val minStr = if (minVal == 0) "00" else minVal.toString()
                canvas.drawText(minStr, x, y, textPaint)
            }
        }
    }

    private fun drawHands(canvas: Canvas) {
        // Hour hand
        val hourAngle = Math.toRadians(((currentHour % 12) * 30.0 + currentMinute * 0.5) - 90.0)
        val hourLen = radius * 0.52f
        canvas.drawLine(cx, cy, (cx + cos(hourAngle) * hourLen).toFloat(), (cy + sin(hourAngle) * hourLen).toFloat(), handHourPaint)

        // Minute hand
        val minuteAngle = Math.toRadians((currentMinute * 6.0) - 90.0)
        val minuteLen = radius * 0.78f
        canvas.drawLine(cx, cy, (cx + cos(minuteAngle) * minuteLen).toFloat(), (cy + sin(minuteAngle) * minuteLen).toFloat(), handMinutePaint)

        // Center hub
        canvas.drawCircle(cx, cy, 14f, handHourPaint)
    }

    private fun drawSelectionTabs(canvas: Canvas) {
        // Hour Tab
        if (selectionMode == Mode.HOUR) {
            canvas.drawRoundRect(tabHourRect, 20f, 20f, selectBgPaint)
            canvas.drawRoundRect(tabHourRect, 20f, 20f, selectRingPaint)
            tabTextPaint.color = ContextCompat.getColor(context, R.color.color_accent_green)
        } else {
            canvas.drawRoundRect(tabHourRect, 20f, 20f, bgPaint)
            canvas.drawRoundRect(tabHourRect, 20f, 20f, borderPaint)
            tabTextPaint.color = ContextCompat.getColor(context, R.color.color_text_primary)
        }
        canvas.drawText("HOUR", tabHourRect.centerX(), tabHourRect.centerY() + 15f, tabTextPaint)

        // Minute Tab
        if (selectionMode == Mode.MINUTE) {
            canvas.drawRoundRect(tabMinuteRect, 20f, 20f, selectBgPaint)
            canvas.drawRoundRect(tabMinuteRect, 20f, 20f, selectRingPaint)
            tabTextPaint.color = ContextCompat.getColor(context, R.color.color_accent_green)
        } else {
            canvas.drawRoundRect(tabMinuteRect, 20f, 20f, bgPaint)
            canvas.drawRoundRect(tabMinuteRect, 20f, 20f, borderPaint)
            tabTextPaint.color = ContextCompat.getColor(context, R.color.color_text_primary)
        }
        canvas.drawText("MINUTE", tabMinuteRect.centerX(), tabMinuteRect.centerY() + 15f, tabTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val ex = event.x
            val ey = event.y

            // Check Sun Button
            if (event.action == MotionEvent.ACTION_DOWN && distance(ex, ey, sunCx, sunCy) < buttonRadius) {
                if (!isAm) {
                    isAm = true
                    vibrate()
                    onTimeChanged?.invoke(currentHour, currentMinute, isAm)
                }
                return true
            }

            // Check Moon Button
            if (event.action == MotionEvent.ACTION_DOWN && distance(ex, ey, moonCx, moonCy) < buttonRadius) {
                if (isAm) {
                    isAm = false
                    vibrate()
                    onTimeChanged?.invoke(currentHour, currentMinute, isAm)
                }
                return true
            }

            // Check Hour Tab
            if (event.action == MotionEvent.ACTION_DOWN && tabHourRect.contains(ex, ey)) {
                if (selectionMode != Mode.HOUR) {
                    selectionMode = Mode.HOUR
                    vibrate()
                }
                return true
            }

            // Check Minute Tab
            if (event.action == MotionEvent.ACTION_DOWN && tabMinuteRect.contains(ex, ey)) {
                if (selectionMode != Mode.MINUTE) {
                    selectionMode = Mode.MINUTE
                    vibrate()
                }
                return true
            }

            // Check Dial interaction
            val dist = distance(ex, ey, cx, cy)
            if (dist > radius * 0.15f && dist < radius * 1.25f) {
                val dx = ex - cx
                val dy = ey - cy
                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())) + 90.0
                if (angle < 0) angle += 360.0

                if (selectionMode == Mode.HOUR) {
                    val hr = (Math.round(angle / 30.0) % 12).toInt()
                    val newHour = if (hr == 0) 12 else hr
                    if (currentHour != newHour) {
                        currentHour = newHour
                        vibrate()
                        onTimeChanged?.invoke(currentHour, currentMinute, isAm)
                    }
                } else {
                    val minVal = (Math.round(angle / 6.0) % 60).toInt()
                    val newMinute = (minVal / 5) * 5 // round to nearest 5 minutes
                    if (currentMinute != newMinute) {
                        currentMinute = newMinute
                        vibrate()
                        onTimeChanged?.invoke(currentHour, currentMinute, isAm)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun vibrate() {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }
}
