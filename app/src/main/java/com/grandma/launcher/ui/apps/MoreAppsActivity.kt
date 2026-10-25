package com.grandma.launcher.ui.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grandma.launcher.R
import com.grandma.launcher.databinding.ActivityMoreAppsBinding
import com.grandma.launcher.remote.DeviceConfigRepository
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper
import com.grandma.launcher.ui.caretaker.CaretakerPinDialog
import com.grandma.launcher.ui.common.InactivityReturn
import com.grandma.launcher.voice.VoiceManager
import com.grandma.launcher.voice.VoiceSlots

/**
 * The apps she was given. Each one is its own icon. A helper pins them from the
 * small settings control after the caretaker PIN.
 */
class MoreAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMoreAppsBinding
    private lateinit var inactivity: InactivityReturn
    private val voice by lazy { VoiceManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityMoreAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            voice.speakSlot(VoiceSlots.HOME, getString(R.string.spoken_home))
            finish()
        }
        binding.btnCaretakerSettings.setOnClickListener {
            CaretakerPinDialog.show(this) {
                startActivity(Intent(this, PinAppsActivity::class.java))
            }
        }
        inactivity = InactivityReturn(this, binding.moreAppsRootLayout)
        binding.rvApps.layoutManager = GridLayoutManager(this, 2)
        CaretakerFabHelper.attach(this, binding.fabCaretaker, binding.btnBack)

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        val topMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_top)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_bottom)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.moreAppsRootLayout) { view, insets ->
            val systemBars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                horizontalMargin,
                topMargin + systemBars.top,
                horizontalMargin,
                bottomMargin + systemBars.bottom
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        showPinned()
        CaretakerFabHelper.syncOverlay(this, binding.fabCaretaker, binding.btnBack)
        inactivity.arm()
    }

    override fun onPause() {
        inactivity.stop()
        super.onPause()
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) inactivity.arm()
        return super.dispatchTouchEvent(ev)
    }

    private fun showPinned() {
        val apps = DeviceConfigRepository(this).visibleApps().mapNotNull { packageName ->
            if (packageManager.getLaunchIntentForPackage(packageName) == null) return@mapNotNull null
            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)
                ).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            val icon = try {
                packageManager.getApplicationIcon(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            PinnedApp(packageName, label, icon)
        }
        binding.tvEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        binding.rvApps.adapter = AppsAdapter(apps) { app ->
            voice.speak(app.label, null)
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
        }
    }

    private class PinnedApp(val packageName: String, val label: String, val icon: Drawable)

    private class AppsAdapter(
        private val apps: List<PinnedApp>,
        private val onAppClick: (PinnedApp) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

        class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_grid, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.icon)
            holder.icon.contentDescription = app.label
            holder.itemView.setOnClickListener { onAppClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}
