package com.grandma.launcher.ui.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom analog clock face.
 *
 * Design decisions:
 * - No numerals — tick marks only. Numeral-free face works for
 *   users who cannot read digits. Tick marks are universally understood.
 * - No second hand — removes visual noise. The user needs to know
 *   roughly what time it is, not the exact second.
 * - Thick, high-contrast hands — easier to read for low-vision users.
 * - Drawn in code, not an image — scales perfectly to any screen density,
 *   hands animate in real time, no assets to maintain.
 * - Updates every minute via ACTION_TIME_TICK broadcast — efficient,
 *   no unnecessary redraws.
 *
 * The clock unregisters its receiver when detached from the window
 * to prevent memory leaks.
 */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Paints ───────────────────────────────────────────────────────────────

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_face)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_border)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.clock_border_width)
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_hand)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.clock_hand_hour_width)
        strokeCap = Paint.Cap.ROUND
    }

    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_hand)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.clock_hand_minute_width)
        strokeCap = Paint.Cap.ROUND
    }

    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_tick_major)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.clock_tick_major_width)
        strokeCap = Paint.Cap.ROUND
    }

    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_tick_minor)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.clock_tick_minor_width)
        strokeCap = Paint.Cap.ROUND
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_clock_center)
        style = Paint.Style.FILL
    }

    // ── State ────────────────────────────────────────────────────────────────

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private val faceRect = RectF()

    // Tick dimensions (computed from radius in onSizeChanged)
    private var majorTickLength = 0f
    private var minorTickLength = 0f
    private var centerDotRadius = 0f
    private var hourHandLength = 0f
    private var minuteHandLength = 0f

    // ── Time receiver ────────────────────────────────────────────────────────

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidate() // Redraw every minute
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeReceiver, filter)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(timeReceiver)
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Clock is always a square — take the smaller of the two dimensions
        val size = min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        centerX = w / 2f
        centerY = h / 2f
        radius = (min(w, h) / 2f) - borderPaint.strokeWidth

        faceRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Scale all dimensions relative to radius
        majorTickLength = radius * 0.14f
        minorTickLength = radius * 0.07f
        centerDotRadius = radius * 0.065f
        hourHandLength = radius * 0.55f
        minuteHandLength = radius * 0.78f
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawFace(canvas)
        drawTicks(canvas)
        drawHands(canvas)
        drawCenterDot(canvas)
    }

    private fun drawFace(canvas: Canvas) {
        canvas.drawOval(faceRect, facePaint)
        canvas.drawOval(faceRect, borderPaint)
    }

    private fun drawTicks(canvas: Canvas) {
        for (i in 0 until 12) {
            val angleDeg = i * 30.0
            val angleRad = Math.toRadians(angleDeg - 90)

            val isMajor = true // All 12 are major — no minor ticks
            val tickLength = if (isMajor) majorTickLength else minorTickLength
            val paint = if (isMajor) majorTickPaint else minorTickPaint

            val outerX = (centerX + cos(angleRad) * radius).toFloat()
            val outerY = (centerY + sin(angleRad) * radius).toFloat()
            val innerX = (centerX + cos(angleRad) * (radius - tickLength)).toFloat()
            val innerY = (centerY + sin(angleRad) * (radius - tickLength)).toFloat()

            canvas.drawLine(innerX, innerY, outerX, outerY, paint)
        }
    }

    private fun drawHands(canvas: Canvas) {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR)        // 0–11
        val minute = now.get(Calendar.MINUTE)    // 0–59

        // Hour hand: each hour = 30°, each minute adds 0.5°
        val hourAngle = (hour * 30f + minute * 0.5f - 90f)
        drawHand(canvas, hourAngle, hourHandLength, hourHandPaint)

        // Minute hand: each minute = 6°
        val minuteAngle = (minute * 6f - 90f)
        drawHand(canvas, minuteAngle, minuteHandLength, minuteHandPaint)
    }

    private fun drawHand(canvas: Canvas, angleDeg: Float, length: Float, paint: Paint) {
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val endX = (centerX + cos(angleRad) * length).toFloat()
        val endY = (centerY + sin(angleRad) * length).toFloat()
        // Draw from slightly behind center for a more natural look
        val startX = (centerX - cos(angleRad) * length * 0.12f).toFloat()
        val startY = (centerY - sin(angleRad) * length * 0.12f).toFloat()
        canvas.drawLine(startX, startY, endX, endY, paint)
    }

    private fun drawCenterDot(canvas: Canvas) {
        canvas.drawCircle(centerX, centerY, centerDotRadius, centerDotPaint)
    }
}
