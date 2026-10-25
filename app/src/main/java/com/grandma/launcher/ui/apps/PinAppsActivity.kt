package com.grandma.launcher.ui.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grandma.launcher.R
import com.grandma.launcher.databinding.ActivityPinAppsBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceConfigRepository
import com.grandma.launcher.remote.DeviceSyncManager
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper
import kotlinx.coroutines.launch

/** Helper list of every launcher app. Checked rows are the pictures on her apps screen. */
class PinAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPinAppsBinding
    private lateinit var rows: List<Row>

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityPinAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pinned = DeviceConfigRepository(this).visibleApps().toSet()
        rows = queryInstalledApps().map { info ->
            val packageName = info.activityInfo.packageName
            Row(
                packageName = packageName,
                label = info.loadLabel(packageManager).toString(),
                icon = info.loadIcon(packageManager),
                checked = packageName in pinned
            )
        }
        binding.rvPinApps.layoutManager = LinearLayoutManager(this)
        binding.rvPinApps.adapter = PinAdapter(rows)
        binding.btnBack.setOnClickListener { finish() }
        CaretakerFabHelper.attach(this, binding.fabCaretaker, binding.btnBack)
        binding.btnSavePins.setOnClickListener { save() }

        val horizontal = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.pinAppsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(horizontal, bars.top, horizontal, bars.bottom)
            insets
        }
    }

    private fun queryInstalledApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun save() {
        val selected = rows.filter { it.checked }.map { it.packageName }
        val repo = DeviceConfigRepository(this)
        repo.setVisibleApps(selected)
        binding.btnSavePins.isEnabled = false
        lifecycleScope.launch {
            when (val result = repo.sync()) {
                is ApiClient.Result.Success ->
                    DeviceSyncManager.getInstance(this@PinAppsActivity).requestSyncNow(force = true)
                is ApiClient.Result.Error ->
                    Toast.makeText(this@PinAppsActivity, result.message, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    private class Row(
        val packageName: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable,
        var checked: Boolean
    )

    private class PinAdapter(private val rows: List<Row>) : RecyclerView.Adapter<PinAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.ivPinIcon)
            val label: TextView = view.findViewById(R.id.tvPinLabel)
            val check: CheckBox = view.findViewById(R.id.cbPin)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pin_app, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = rows[position]
            holder.icon.setImageDrawable(row.icon)
            holder.label.text = row.label
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = row.checked
            holder.check.setOnCheckedChangeListener { _, isChecked -> row.checked = isChecked }
            holder.itemView.setOnClickListener {
                holder.check.isChecked = !holder.check.isChecked
            }
        }

        override fun getItemCount() = rows.size
    }
}
