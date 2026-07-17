package com.grandma.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences

/**
 * SOS button with hold-to-activate interaction.
 *
 * Why hold, not tap:
 * Elderly users with tremor frequently trigger buttons accidentally.
 * A tap-to-activate SOS is dangerous — a pocket press, a fumble, or
 * a reflex grab could call emergency services unexpectedly.
 *
 * The hold interaction design:
 * 1. User presses → short vibration (I registered your press)
 * 2. Progress ring fills over 3 seconds (visual progress)
 * 3. Vibration pulses at 1s and 2s (haptic milestones — no reading needed)
 * 4. At 3s: long burst vibration → callback triggered → emergency call
 * 5. Released early: ring resets, no action, no further vibration
 *
 * The ring is drawn as a white stroke around the button border,
 * filling clockwise from the top. It's highly visible against the red.
 */
class SosButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Callback ─────────────────────────────────────────────────────────────

    /** Called when the hold duration completes — trigger the emergency call. */
    var onSosActivated: (() -> Unit)? = null

    // ── Constants ────────────────────────────────────────────────────────────

    private val holdDurationMs = AppPreferences.SOS_HOLD_DURATION_MS
    private val ringStrokeWidth = resources.getDimension(R.dimen.sos_progress_ring_stroke)
    private val cornerRadius = resources.getDimension(R.dimen.sos_button_corner_radius)

    // ── Paints ───────────────────────────────────────────────────────────────

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_sos_red)
        style = Paint.Style.FILL
    }

    private val backgroundPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_sos_red_dark)
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_on_dark)
        style = Paint.Style.STROKE
        strokeWidth = ringStrokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_text_on_dark)
        textSize = resources.getDimension(R.dimen.space_xl).coerceAtLeast(
            context.resources.displayMetrics.scaledDensity * 24
        )
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    // ── State ────────────────────────────────────────────────────────────────

    private var isPressed = false
    private var holdProgress = 0f          // 0.0 → 1.0
    private var pressStartTime = 0L
    private val ringRect = RectF()
    private val fullSosPath = Path()
    private val progressPath = Path()

    private val vibrator: Vibrator? = context.getSystemService()

    // Runnable that updates progress while the button is held
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isPressed) return

            val elapsed = System.currentTimeMillis() - pressStartTime
            holdProgress = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)

            triggerMilestoneVibration(elapsed)
            invalidate()

            if (elapsed >= holdDurationMs) {
                onHoldComplete()
            } else {
                postDelayed(this, 16) // ~60fps updates
            }
        }
    }

    private var vibrated1s = false
    private var vibrated2s = false

    // ── Vibration ────────────────────────────────────────────────────────────

    private fun vibrateShort() = vibrate(15)
    private fun vibrateMedium() = vibrate(30)
    private fun vibrateStrong() = vibrate(50)
    private fun vibrateBurst() = vibrate(200)

    private fun vibrate(durationMs: Long) {
        vibrator?.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun triggerMilestoneVibration(elapsedMs: Long) {
        if (elapsedMs >= 1000 && !vibrated1s) {
            vibrated1s = true
            vibrateMedium()
        }
        if (elapsedMs >= 2000 && !vibrated2s) {
            vibrated2s = true
            vibrateStrong()
        }
    }

    // ── Touch handling ───────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startHold()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                true
            }
            else -> false
        }
    }

    private fun startHold() {
        isPressed = true
        pressStartTime = System.currentTimeMillis()
        vibrated1s = false
        vibrated2s = false
        holdProgress = 0f
        vibrateShort()
        invalidate()
        post(progressRunnable)
    }

    private fun cancelHold() {
        isPressed = false
        removeCallbacks(progressRunnable)
        // Animate ring back to 0
        animateRingReset()
    }

    private fun animateRingReset() {
        val startProgress = holdProgress
        val startTime = System.currentTimeMillis()
        val resetDuration = 300L

        val resetRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed.toFloat() / resetDuration).coerceIn(0f, 1f)
                holdProgress = startProgress * (1f - fraction)
                invalidate()
                if (fraction < 1f) postDelayed(this, 16)
            }
        }
        post(resetRunnable)
    }

    private fun onHoldComplete() {
        isPressed = false
        holdProgress = 0f
        vibrateBurst()
        invalidate()
        onSosActivated?.invoke()
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = ringStrokeWidth / 2f
        ringRect.set(inset, inset, w - inset, h - inset)

        // Compute the path tracing the exact rounded rectangle boundary
        fullSosPath.reset()
        val left = inset
        val top = inset
        val right = w.toFloat() - inset
        val bottom = h.toFloat() - inset
        val cx = w.toFloat() / 2f

        // Start at top center
        fullSosPath.moveTo(cx, top)
        // Line to top right corner start
        fullSosPath.lineTo(right - cornerRadius, top)
        // Top-Right corner curve
        fullSosPath.arcTo(
            RectF(right - 2 * cornerRadius, top, right, top + 2 * cornerRadius),
            -90f,
            90f,
            false
        )
        // Line to bottom right corner start
        fullSosPath.lineTo(right, bottom - cornerRadius)
        // Bottom-Right corner curve
        fullSosPath.arcTo(
            RectF(right - 2 * cornerRadius, bottom - 2 * cornerRadius, right, bottom),
            0f,
            90f,
            false
        )
        // Line to bottom left corner start
        fullSosPath.lineTo(left + cornerRadius, bottom)
        // Bottom-Left corner curve
        fullSosPath.arcTo(
            RectF(left, bottom - 2 * cornerRadius, left + 2 * cornerRadius, bottom),
            90f,
            90f,
            false
        )
        // Line to top left corner start
        fullSosPath.lineTo(left, top + cornerRadius)
        // Top-Left corner curve
        fullSosPath.arcTo(
            RectF(left, top, left + 2 * cornerRadius, top + 2 * cornerRadius),
            180f,
            90f,
            false
        )
        // Line back to top center
        fullSosPath.lineTo(cx, top)
    }

    // ── Drawing ──────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Background
        val bgPaint = if (isPressed) backgroundPressedPaint else backgroundPaint
        canvas.drawRoundRect(0f, 0f, w, h, cornerRadius, cornerRadius, bgPaint)

        // Progress boundary outline — draws clockwise from top center
        if (holdProgress > 0f) {
            progressPath.reset()
            val pathMeasure = PathMeasure(fullSosPath, false)
            val length = pathMeasure.length
            pathMeasure.getSegment(0f, length * holdProgress, progressPath, true)
            canvas.drawPath(progressPath, ringPaint)
        }

        // "SOS" label — centered
        val textY = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("SOS", w / 2f, textY, textPaint)
    }
}
