package com.grandma.launcher.ui.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grandma.launcher.R
import com.grandma.launcher.databinding.ActivityMoreAppsBinding
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper

/**
 * Displays all installed apps in a grid.
 *
 * Why is this a separate screen and not the home screen?
 * The home screen must have absolute zero cognitive load.
 * Showing 30+ app icons would overwhelm the user and
 * make the important things (contacts, SOS) harder to find.
 *
 * This screen exists as an "advanced" area — for when the user
 * gains confidence and wants to explore. It's not hidden
 * (there's a visible "More Apps" link on home) but it's
 * also not prominent.
 *
 * The grid uses 3 columns — matching the contacts screen for consistency.
 * Large icons (64dp) with labels below.
 *
 * We exclude the launcher itself from this list.
 */
class MoreAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMoreAppsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMoreAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        val apps = queryInstalledApps()
        binding.rvApps.layoutManager = GridLayoutManager(this, 3)
        binding.rvApps.adapter = AppsAdapter(apps) { resolveInfo ->
            launchApp(resolveInfo)
        }

        CaretakerFabHelper.attach(this, binding.fabCaretaker)
    }

    private fun queryInstalledApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName } // Exclude self
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun launchApp(resolveInfo: ResolveInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(
            resolveInfo.activityInfo.packageName
        ) ?: return
        startActivity(launchIntent)
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private inner class AppsAdapter(
        private val apps: List<ResolveInfo>,
        private val onAppClick: (ResolveInfo) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivAppIcon)
            val label: TextView = view.findViewById(R.id.tvAppLabel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_grid, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.loadIcon(packageManager))
            holder.label.text = app.loadLabel(packageManager)
            holder.itemView.setOnClickListener { onAppClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}
