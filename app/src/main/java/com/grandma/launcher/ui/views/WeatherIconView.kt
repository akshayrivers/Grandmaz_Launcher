package com.grandma.launcher.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.grandma.launcher.data.WeatherCondition

/**
 * Custom view that draws a premium weather icon using Canvas paths and paint shapes.
 * Operates independently of theme configurations or resource images.
 */
class WeatherIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var condition: WeatherCondition = WeatherCondition.SUNNY

    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5B041") // Soft orange/yellow sun
        style = Paint.Style.FILL
    }

    private val sunRayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5B041")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D5D8DC") // Soft cloud grey
        style = Paint.Style.FILL
    }

    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5DADE2") // Rain blue
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F4D03F") // Lightning yellow
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
    }

    fun setCondition(newCondition: WeatherCondition) {
        condition = newCondition
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = 140
        val w = resolveSize(desiredSize, widthMeasureSpec)
        val h = resolveSize(desiredSize, heightMeasureSpec)
        val size = Math.min(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.save()
        when (condition) {
            WeatherCondition.SUNNY -> drawSunny(canvas, w, h)
            WeatherCondition.CLOUDY -> drawCloudy(canvas, w, h)
            WeatherCondition.RAINY -> drawRainy(canvas, w, h)
            WeatherCondition.THUNDERSTORM -> drawThunderstorm(canvas, w, h)
            WeatherCondition.SNOWY -> drawSnowy(canvas, w, h)
        }
        canvas.restore()
    }

    private fun drawSunny(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val sunRadius = w * 0.25f

        canvas.drawCircle(cx, cy, sunRadius, sunPaint)

        val rayStart = sunRadius + 10f
        val rayEnd = sunRadius + 24f
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45.0))
            val startX = (cx + Math.cos(angle) * rayStart).toFloat()
            val startY = (cy + Math.sin(angle) * rayStart).toFloat()
            val endX = (cx + Math.cos(angle) * rayEnd).toFloat()
            val endY = (cy + Math.sin(angle) * rayEnd).toFloat()
            canvas.drawLine(startX, startY, endX, endY, sunRayPaint)
        }
    }

    private fun drawCloudy(canvas: Canvas, w: Float, h: Float) {
        drawCloudShape(canvas, w * 0.5f, h * 0.5f, w * 0.6f)
    }

    private fun drawCloudShape(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val rBase = size * 0.2f
        canvas.drawCircle(cx - rBase * 1.2f, cy + rBase * 0.2f, rBase * 0.8f, cloudPaint)
        canvas.drawCircle(cx + rBase * 1.2f, cy + rBase * 0.2f, rBase * 0.8f, cloudPaint)
        canvas.drawCircle(cx, cy - rBase * 0.4f, rBase * 1.2f, cloudPaint)
        canvas.drawCircle(cx - rBase * 0.5f, cy, rBase, cloudPaint)
        canvas.drawCircle(cx + rBase * 0.5f, cy, rBase, cloudPaint)

        val rectLeft = cx - rBase * 1.2f
        val rectTop = cy - rBase * 0.1f
        val rectRight = cx + rBase * 1.2f
        val rectBottom = cy + rBase * 0.8f
        canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, cloudPaint)
    }

    private fun drawRainy(canvas: Canvas, w: Float, h: Float) {
        drawCloudShape(canvas, w * 0.5f, h * 0.4f, w * 0.6f)

        val startY = h * 0.62f
        val drop1X = w * 0.38f
        val drop2X = w * 0.5f
        val drop3X = w * 0.62f

        canvas.drawLine(drop1X, startY, drop1X - 6f, startY + 20f, rainPaint)
        canvas.drawLine(drop2X, startY + 8f, drop2X - 6f, startY + 28f, rainPaint)
        canvas.drawLine(drop3X, startY, drop3X - 6f, startY + 20f, rainPaint)
    }

    private fun drawThunderstorm(canvas: Canvas, w: Float, h: Float) {
        val originalColor = cloudPaint.color
        cloudPaint.color = Color.parseColor("#7F8C8D") // Dark thunderstorm cloud
        drawCloudShape(canvas, w * 0.5f, h * 0.4f, w * 0.6f)
        cloudPaint.color = originalColor

        val cx = w * 0.5f
        val cy = h * 0.7f
        val path = Path().apply {
            moveTo(cx + 8f, cy - 20f)
            lineTo(cx - 15f, cy + 5f)
            lineTo(cx - 2f, cy + 5f)
            lineTo(cx - 10f, cy + 25f)
            lineTo(cx + 15f, cy - 2f)
            lineTo(cx + 2f, cy - 2f)
            close()
        }
        canvas.drawPath(path, lightningPaint)
    }

    private fun drawSnowy(canvas: Canvas, w: Float, h: Float) {
        drawCloudShape(canvas, w * 0.5f, h * 0.4f, w * 0.6f)

        val startY = h * 0.68f
        val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BDC3C7")
            style = Paint.Style.FILL
        }

        canvas.drawCircle(w * 0.35f, startY, 6f, snowPaint)
        canvas.drawCircle(w * 0.5f, startY + 8f, 6f, snowPaint)
        canvas.drawCircle(w * 0.65f, startY, 6f, snowPaint)
        canvas.drawCircle(w * 0.42f, startY + 18f, 5f, snowPaint)
        canvas.drawCircle(w * 0.58f, startY + 18f, 5f, snowPaint)
    }
}
