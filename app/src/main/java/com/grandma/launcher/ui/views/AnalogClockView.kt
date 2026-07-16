package com.grandma.launcher.ui.views

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Analog clock — drawn entirely in code, zero theme dependency.
 *
 * Root cause of previous blank clock: onMeasure was returning size=0
 * when the MeasureSpec mode was AT_MOST or UNSPECIFIED (which ConstraintLayout
 * uses before the first layout pass). The fix is to respect the spec mode:
 * use the spec size for EXACTLY/AT_MOST, and fall back to a sensible default
 * for UNSPECIFIED.
 */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Paints — ALL hardcoded, never from theme ──────────────────────────────

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D9D3CB")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val hourHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
    }

    private val minuteHandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
    }

    // ── Geometry — computed in onSizeChanged ──────────────────────────────────

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private val faceRect = RectF()

    // ── Time receiver ─────────────────────────────────────────────────────────

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            invalidate()
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
        invalidate() // Draw immediately on attach
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(timeReceiver) } catch (_: Exception) {}
    }

    // ── Measure — THE critical fix ────────────────────────────────────────────
    // Previous version called MeasureSpec.getSize() unconditionally.
    // When mode is UNSPECIFIED, getSize() returns 0 → radius=0 → nothing draws.
    // We must handle all three modes correctly.

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 540 // fallback in px if parent gives no constraint

        val w = resolveSize(desiredSize, widthMeasureSpec)
        val h = resolveSize(desiredSize, heightMeasureSpec)
        val size = min(w, h) // always square
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = (min(w, h) / 2f) - borderPaint.strokeWidth / 2f
        faceRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    // ── Draw ──────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        if (radius <= 0f) return // safety — nothing to draw

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
        val hour = now.get(Calendar.HOUR)
        val minute = now.get(Calendar.MINUTE)

        val hourAngle = Math.toRadians((hour * 30.0 + minute * 0.5) - 90.0)
        val minuteAngle = Math.toRadians((minute * 6.0) - 90.0)

        val hourLen = radius * 0.52f
        val minuteLen = radius * 0.76f
        val tailRatio = 0.15f

        // Hour hand
        canvas.drawLine(
            (cx - cos(hourAngle) * hourLen * tailRatio).toFloat(),
            (cy - sin(hourAngle) * hourLen * tailRatio).toFloat(),
            (cx + cos(hourAngle) * hourLen).toFloat(),
            (cy + sin(hourAngle) * hourLen).toFloat(),
            hourHandPaint
        )

        // Minute hand
        canvas.drawLine(
            (cx - cos(minuteAngle) * minuteLen * tailRatio).toFloat(),
            (cy - sin(minuteAngle) * minuteLen * tailRatio).toFloat(),
            (cx + cos(minuteAngle) * minuteLen).toFloat(),
            (cy + sin(minuteAngle) * minuteLen).toFloat(),
            minuteHandPaint
        )
    }

    private fun drawCenterDot(canvas: Canvas) {
        canvas.drawCircle(cx, cy, radius * 0.06f, centerDotPaint)
    }
}
