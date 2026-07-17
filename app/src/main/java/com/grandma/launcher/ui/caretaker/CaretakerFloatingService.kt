package com.grandma.launcher.ui.caretaker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import kotlin.math.abs

class CaretakerFloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: AppPreferences

    private val fadeRunnable = Runnable {
        floatingView?.animate()
            ?.alpha(0.25f)
            ?.setDuration(400L)
            ?.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_GrandmasLauncher)
            floatingView = LayoutInflater.from(contextThemeWrapper).inflate(R.layout.layout_floating_caretaker, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                // Place initially in bottom left
                val displayMetrics = resources.displayMetrics
                x = 30
                y = displayMetrics.heightPixels - 500
            }

            windowManager.addView(floatingView, params)

            setupTouchListener()
            resetIdleTimer()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun setupTouchListener() {
        val view = floatingView ?: return

        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var startClickTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        startClickTime = System.currentTimeMillis()

                        // Make active immediately
                        view.animate().cancel()
                        view.alpha = 1.0f
                        handler.removeCallbacks(fadeRunnable)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params?.let {
                            it.x = initialX + dx
                            it.y = initialY + dy
                            windowManager.updateViewLayout(view, it)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startClickTime
                        val dx = abs(event.rawX - initialTouchX)
                        val dy = abs(event.rawY - initialTouchY)

                        if (duration < 250 && dx < 10 && dy < 10) {
                            // Tap! Launch the CaretakerHelpActivity
                            val intent = Intent(this@CaretakerFloatingService, CaretakerHelpActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(intent)
                        }
                        resetIdleTimer()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(fadeRunnable)
        floatingView?.animate()?.alpha(1.0f)?.setDuration(200L)?.start()
        handler.postDelayed(fadeRunnable, prefs.fabIdleDelayMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
    }
}
