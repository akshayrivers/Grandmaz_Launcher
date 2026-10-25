package com.grandma.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Angles are degrees clockwise from 12 o'clock. Seconds are included so the
 * minute hand keeps moving while the clock is on screen.
 */
internal object AnalogClockMath {
    data class Hands(val hourDegrees: Float, val minuteDegrees: Float, val secondDegrees: Float)

    fun hands(hourOfHalfDay: Int, minute: Int, second: Int, millis: Int): Hands {
        val seconds = second + millis / 1000f
        val minutes = minute + seconds / 60f
        val hours = (hourOfHalfDay % 12) + minutes / 60f
        return Hands(
            hourDegrees = hours * 30f,
            minuteDegrees = minutes * 6f,
            secondDegrees = seconds * 6f
        )
    }
}

/**
 * Analog clock. Tick marks only, no numerals.
 *
 * Paint colours are the clock colour resources (ContextCompat, not a theme
 * attribute). A one-second tick runs while the view is visible so the hands
 * follow the real local time.
 */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }
    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val secondHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        applyPalette()
    }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private val faceRect = RectF()

    private val ticker = Runnable { pulse() }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTicking()
    }

    override fun onDetachedFromWindow() {
        stopTicking()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) startTicking() else stopTicking()
    }

    private fun startTicking() {
        mainHandler.removeCallbacks(ticker)
        mainHandler.post(ticker)
    }

    private fun stopTicking() {
        mainHandler.removeCallbacks(ticker)
    }

    private fun pulse() {
        invalidate()
        mainHandler.postDelayed(ticker, TICK_MS)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 540
        val w = resolveSize(desiredSize, widthMeasureSpec)
        val h = resolveSize(desiredSize, heightMeasureSpec)
        val size = min(w, h).coerceAtLeast(1)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = (min(w, h) / 2f) - borderPaint.strokeWidth / 2f
        faceRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    private fun applyPalette() {
        facePaint.color = ContextCompat.getColor(context, R.color.color_clock_face)
        borderPaint.color = ContextCompat.getColor(context, R.color.color_clock_border)
        hourHandPaint.color = ContextCompat.getColor(context, R.color.color_clock_hand_hour)
        val minuteInk = ContextCompat.getColor(context, R.color.color_clock_hand_minute)
        minuteHandPaint.color = minuteInk
        secondHandPaint.color = minuteInk
        tickPaint.color = ContextCompat.getColor(context, R.color.color_clock_tick_major)
        centerDotPaint.color = ContextCompat.getColor(context, R.color.color_clock_center)
    }

    override fun onDraw(canvas: Canvas) {
        if (radius <= 0f) return
        applyPalette()
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
        val tickOuter = radius - borderPaint.strokeWidth
        val tickInner = tickOuter - radius * 0.13f
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30.0) - 90.0)
            canvas.drawLine(
                (cx + cos(angle) * tickInner).toFloat(),
                (cy + sin(angle) * tickInner).toFloat(),
                (cx + cos(angle) * tickOuter).toFloat(),
                (cy + sin(angle) * tickOuter).toFloat(),
                tickPaint
            )
        }
    }

    private fun drawHands(canvas: Canvas) {
        val now = Calendar.getInstance()
        val hands = AnalogClockMath.hands(
            now.get(Calendar.HOUR),
            now.get(Calendar.MINUTE),
            now.get(Calendar.SECOND),
            now.get(Calendar.MILLISECOND)
        )
        drawHand(canvas, hands.hourDegrees, radius * 0.52f, hourHandPaint)
        drawHand(canvas, hands.minuteDegrees, radius * 0.76f, minuteHandPaint)
        drawHand(canvas, hands.secondDegrees, radius * 0.82f, secondHandPaint)
    }

    private fun drawHand(canvas: Canvas, degreesFromTwelve: Float, length: Float, paint: Paint) {
        val angle = Math.toRadians(degreesFromTwelve.toDouble() - 90.0)
        val tail = length * 0.15f
        canvas.drawLine(
            (cx - cos(angle) * tail).toFloat(),
            (cy - sin(angle) * tail).toFloat(),
            (cx + cos(angle) * length).toFloat(),
            (cy + sin(angle) * length).toFloat(),
            paint
        )
    }

    private fun drawCenterDot(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius * 0.06f, centerDotPaint)
    }

    companion object {
        private const val TICK_MS = 1000L
    }
}
