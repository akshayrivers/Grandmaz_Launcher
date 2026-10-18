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
import android.view.View
import android.view.WindowManager
import com.grandma.launcher.R
import com.grandma.launcher.ui.home.HomeActivity

/**
 * Draws the caretaker and house buttons over other apps.
 * Bottom-left is help. Bottom-right is home. Neither fades or moves.
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
        val side = resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_horizontal)
        val bottom = resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_bottom) + navigationBarHeight()
        addOverlay(R.layout.layout_floating_caretaker, Gravity.BOTTOM or Gravity.START, side, bottom) {
            startActivity(Intent(this, CaretakerHelpActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }
        addOverlay(R.layout.layout_floating_house, Gravity.BOTTOM or Gravity.END, side, bottom) {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }
    }

    private fun navigationBarHeight(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return windowManager.currentWindowMetrics.windowInsets
            .getInsets(android.view.WindowInsets.Type.navigationBars())
            .bottom
    }

    private fun addOverlay(layout: Int, gravity: Int, x: Int, y: Int, onClick: () -> Unit) {
        try {
            val themed = ContextThemeWrapper(this, R.style.Theme_GrandmasLauncher)
            val view = LayoutInflater.from(themed).inflate(layout, null)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.gravity = gravity
                this.x = x
                this.y = y
            }
            view.setOnClickListener { onClick() }
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
}
