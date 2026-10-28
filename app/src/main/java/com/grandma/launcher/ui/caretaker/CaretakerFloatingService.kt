package com.grandma.launcher.ui.caretaker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.ui.home.HomeActivity
import kotlin.math.abs

/**
 * Draws help and home over other apps. Both can be dragged. A short tap still
 * opens help or home. Gallery and YouTube stay on the home screen.
 */
class CaretakerFloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private val overlays = mutableListOf<View>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val size = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        val marginX = resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_horizontal)
        val marginY = resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_bottom) + navigationBarHeight()
        val (screenW, screenH) = screenSize()
        val prefs = AppPreferences(this)
        addOverlay(R.layout.layout_floating_caretaker, help = true, prefs, screenW, screenH, size, marginX, marginY) {
            startActivity(Intent(this, CaretakerHelpActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }
        addOverlay(R.layout.layout_floating_house, help = false, prefs, screenW, screenH, size, marginX, marginY) {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun navigationBarHeight(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return windowManager.currentWindowMetrics.windowInsets
            .getInsets(android.view.WindowInsets.Type.navigationBars())
            .bottom
    }

    private fun addOverlay(
        layout: Int,
        help: Boolean,
        prefs: AppPreferences,
        screenW: Int,
        screenH: Int,
        size: Int,
        marginX: Int,
        marginY: Int,
        onClick: () -> Unit
    ) {
        try {
            val themed = ContextThemeWrapper(this, R.style.Theme_GrandmasLauncher)
            val view = LayoutInflater.from(themed).inflate(layout, null)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val saved = prefs.overlayPosition(help)
            val (x, y) = if (saved == null) {
                cornerPosition(screenW, screenH, size, marginX, marginY, alignEnd = !help)
            } else {
                clampToScreen(saved.first, saved.second, screenW, screenH, size)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = x
                this.y = y
            }
            val slop = ViewConfiguration.get(this).scaledTouchSlop
            view.setOnTouchListener(object : View.OnTouchListener {
                private var originX = 0
                private var originY = 0
                private var downRawX = 0f
                private var downRawY = 0f
                private var dragging = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            originX = params.x
                            originY = params.y
                            downRawX = event.rawX
                            downRawY = event.rawY
                            dragging = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downRawX
                            val dy = event.rawY - downRawY
                            if (!dragging && !isDrag(dx, dy, slop)) return true
                            dragging = true
                            val (sw, sh) = screenSize()
                            val (nextX, nextY) = clampToScreen(
                                originX + dx.toInt(),
                                originY + dy.toInt(),
                                sw,
                                sh,
                                size
                            )
                            params.x = nextX
                            params.y = nextY
                            try {
                                windowManager.updateViewLayout(v, params)
                            } catch (_: Exception) {
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (dragging) prefs.setOverlayPosition(help, params.x, params.y) else onClick()
                            return true
                        }
                        MotionEvent.ACTION_CANCEL -> return true
                    }
                    return false
                }
            })
            windowManager.addView(view, params)
            overlays.add(view)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        overlays.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
        overlays.clear()
        super.onDestroy()
    }

    companion object {
        /** True once the finger has moved far enough that this is a drag, not a tap. */
        fun isDrag(dx: Float, dy: Float, slop: Int): Boolean {
            val limit = slop.toFloat()
            return abs(dx) > limit || abs(dy) > limit
        }

        /** Keeps a button fully on screen. */
        fun clampToScreen(x: Int, y: Int, screenWidth: Int, screenHeight: Int, size: Int): Pair<Int, Int> {
            val maxX = (screenWidth - size).coerceAtLeast(0)
            val maxY = (screenHeight - size).coerceAtLeast(0)
            return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
        }

        /** Help is the start corner. Home is the end corner. Both sit on the bottom. */
        fun cornerPosition(
            screenWidth: Int,
            screenHeight: Int,
            size: Int,
            marginX: Int,
            marginY: Int,
            alignEnd: Boolean
        ): Pair<Int, Int> {
            val x = if (alignEnd) screenWidth - marginX - size else marginX
            val y = screenHeight - marginY - size
            return clampToScreen(x, y, screenWidth, screenHeight, size)
        }
    }
}
