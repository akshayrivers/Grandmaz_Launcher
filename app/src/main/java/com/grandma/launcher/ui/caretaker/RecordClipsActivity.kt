package com.grandma.launcher.ui.caretaker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grandma.launcher.BuildConfig
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.ActivityRecordClipsBinding
import com.grandma.launcher.remote.LocalHelpStore
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceConfigRepository
import com.grandma.launcher.remote.DeviceRegistrationRepository
import com.grandma.launcher.remote.DeviceSyncManager
import com.grandma.launcher.ui.alarm.AlarmActivity
import com.grandma.launcher.ui.apps.PinAppsActivity
import com.grandma.launcher.voice.ClipRecorder
import com.grandma.launcher.voice.VoiceManager
import com.grandma.launcher.voice.VoiceSlots
import kotlinx.coroutines.launch
import java.io.File

/** Helper screen. Hold a microphone to replace that button's words with a recording. */
class RecordClipsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordClipsBinding
    private val voice by lazy { VoiceManager(this) }
    private var recorder: ClipRecorder? = null
    private var recordingSlot: VoiceSlots.Slot? = null

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityRecordClipsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rvSlots.layoutManager = LinearLayoutManager(this)
        binding.rvSlots.adapter = SlotAdapter(VoiceSlots.all, this::begin, this::end)
        CaretakerFabHelper.attach(this, binding.fabCaretaker)
        binding.btnOpenAlarms.setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java))
        }
        binding.btnOpenPins.setOnClickListener {
            startActivity(Intent(this, PinAppsActivity::class.java))
        }
        binding.btnEditHome.setOnClickListener {
            startActivity(Intent(this, CaretakerConfigActivity::class.java))
        }
        binding.btnAddCaretaker.setOnClickListener {
            startActivity(Intent(this, CaretakerConfigActivity::class.java))
        }
        if (BuildConfig.DEBUG) {
            binding.localFlowPanel.visibility = View.VISIBLE
            val prefs = AppPreferences(this)
            binding.switchLocalFlow.isChecked = prefs.localFlowEnabled
            binding.switchLocalFlowFail.isChecked = prefs.localFlowFailNextHelp
            binding.switchLocalFlow.setOnCheckedChangeListener { _, checked ->
                prefs.localFlowEnabled = checked
                refreshLocalFlowStatus()
            }
            binding.switchLocalFlowFail.setOnCheckedChangeListener { _, checked ->
                prefs.localFlowFailNextHelp = checked
            }
            refreshLocalFlowStatus()
        }
        val horizontal = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.recordClipsRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(horizontal, bars.top, horizontal, bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) refreshLocalFlowStatus()
    }

    private fun refreshLocalFlowStatus() {
        val saved = LocalHelpStore.entries(this)
        binding.tvLocalFlowStatus.text = if (saved.isEmpty()) {
            getString(R.string.local_flow_status_empty)
        } else {
            getString(R.string.local_flow_status_count, saved.size, saved.last().type)
        }
    }

    override fun onDestroy() {
        recorder?.cancel()
        recorder = null
        voice.shutdown()
        super.onDestroy()
    }

    private fun begin(slot: VoiceSlots.Slot, button: ImageButton) {
        if (recorder != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val next = ClipRecorder(File(filesDir, "voice/recording.m4a"), DeviceRegistrationRepository(this))
        try {
            next.start()
        } catch (_: Exception) {
            return
        }
        recorder = next
        recordingSlot = slot
        button.setBackgroundColor(Color.WHITE)
        button.imageTintList = ColorStateList.valueOf(Color.BLACK)
    }

    private fun end(button: ImageButton) {
        button.setBackgroundResource(R.drawable.bg_control)
        button.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.color_text_primary))
        val active = recorder ?: return
        val slot = recordingSlot ?: return
        recorder = null
        recordingSlot = null
        lifecycleScope.launch {
            when (val result = active.stopAndUpload()) {
                is ApiClient.Result.Success -> keep(slot, result.data)
                is ApiClient.Result.Error -> keepLocal(slot)
            }
        }
    }

    private fun keep(slot: VoiceSlots.Slot, assetId: String) {
        val repo = DeviceConfigRepository(this)
        repo.saveClip(slot.id, assetId)
        voice.speakSlot(slot.id, getString(slot.spoken))
        lifecycleScope.launch {
            when (val result = repo.sync()) {
                is ApiClient.Result.Success ->
                    DeviceSyncManager.getInstance(this@RecordClipsActivity).requestSyncNow(force = true)
                is ApiClient.Result.Error ->
                    Toast.makeText(this@RecordClipsActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun keepLocal(slot: VoiceSlots.Slot) {
        val source = File(filesDir, "voice/recording.m4a")
        if (!source.exists()) return
        val id = "local-${System.currentTimeMillis()}"
        val stored = File(filesDir, "voice/$id.m4a")
        if (source.renameTo(stored)) keep(slot, id)
    }

    private class SlotAdapter(
        private val slots: List<VoiceSlots.Slot>,
        private val onDown: (VoiceSlots.Slot, ImageButton) -> Unit,
        private val onUp: (ImageButton) -> Unit
    ) : RecyclerView.Adapter<SlotAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val label: TextView = view.findViewById(R.id.tvSlotLabel)
            val mic: ImageButton = view.findViewById(R.id.btnSlotMic)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_record_slot, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val slot = slots[position]
            holder.label.setText(slot.label)
            holder.mic.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onDown(slot, holder.mic)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        onUp(holder.mic)
                        view.performClick()
                    }
                }
                true
            }
        }

        override fun getItemCount() = slots.size
    }
}
