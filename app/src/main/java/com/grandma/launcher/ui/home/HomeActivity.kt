package com.grandma.launcher.ui.home

import android.content.Intent
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.GridLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView
import com.grandma.launcher.R
import com.grandma.launcher.appScope
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.databinding.ActivityHomeBinding
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceSyncManager
import com.grandma.launcher.remote.SosCoordinator
import com.grandma.launcher.ui.apps.MoreAppsActivity
import com.grandma.launcher.ui.assistant.TalkActivity
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper
import com.grandma.launcher.ui.common.InstalledApp
import com.grandma.launcher.ui.caretaker.CaretakerPinDialog
import com.grandma.launcher.ui.common.CallLauncher
import com.grandma.launcher.ui.contacts.AddContactActivity
import com.grandma.launcher.ui.contacts.ContactsActivity
import com.grandma.launcher.ui.contacts.CallConfirmBottomSheet
import com.grandma.launcher.ui.message.MessageActivity
import com.grandma.launcher.ui.caretaker.RecordClipsActivity
import com.grandma.launcher.voice.Speaker
import com.grandma.launcher.voice.VoiceManager
import com.grandma.launcher.voice.VoiceSlots
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity(), Speaker {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var contactRepo: ContactRepository
    private lateinit var appPrefs: AppPreferences
    private val callLauncher = CallLauncher(this)
    private val sosCoordinator by lazy { SosCoordinator(this, appScope) }
    private val voice by lazy { VoiceManager(this) }

    override fun speak(text: String, clipId: String?) = voice.speak(text, clipId)
    override fun speakSlot(slot: String, text: String) = voice.speakSlot(slot, text)

    companion object {
        private const val TAG = "HomeActivity"
        // Max contacts shown on home screen — 4 keeps touch targets large
        const val MAX_HOME_CONTACTS = 4
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Tell the window to draw edge-to-edge (behind status + nav bars).
        // Without this the system bars are opaque and insets come back as 0
        // so our padding listener does nothing.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactRepo = ContactRepository(this)
        appPrefs = AppPreferences(this)

        if (!appPrefs.isSetupComplete) {
            startActivity(Intent(this, com.grandma.launcher.ui.setup.SetupActivity::class.java))
            finish()
            return
        }

        // Apply window insets while keeping our design-system margins.
        // We only respect the status bar inset; the bottom spacing comes
        // from our own layout so the launcher doesn't waste vertical space
        // on gesture navigation devices.

        val horizontalMargin =
    resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)

        val topMargin =
            resources.getDimensionPixelSize(R.dimen.screen_margin_top)

        val bottomMargin =
            resources.getDimensionPixelSize(R.dimen.screen_margin_bottom)

        val fabBottomMargin =
            resources.getDimensionPixelSize(R.dimen.fab_caretaker_margin_bottom)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->

            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )

            view.updatePadding(
                left = horizontalMargin,
                top = topMargin + systemBars.top,
                right = horizontalMargin,
                bottom = bottomMargin
            )

            binding.fabCaretaker.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                this.bottomMargin = fabBottomMargin + systemBars.bottom
            }
            binding.btnHouse.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                this.bottomMargin = fabBottomMargin + systemBars.bottom
            }
            // Root padding does not include the nav bar. The scroll's own
            // bottom padding keeps the last row above the lifted buttons.
            binding.homeScroll.updatePadding(bottom = systemBars.bottom)

            insets
        }

        ViewCompat.requestApplyInsets(binding.rootLayout)

        // The home screen is the root: back does nothing.
        onBackPressedDispatcher.addCallback(this) { }

        setupDate()
        setupClockHold()
        setupHouse()
        setupFab()
        setupToolButtons()
        setupSosButton()
        setupDestinations()
    }

    override fun onDestroy() {
        voice.shutdown()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Refresh contacts every time we return to home
        // (user may have added/removed a contact)
        loadContacts()
        if (appPrefs.isSetupComplete) {
            CaretakerFabHelper.syncOverlay(this, binding.fabCaretaker, binding.btnHouse)
            if (CaretakerFabHelper.askOverlayPermission(this)) {
                voice.speak(getString(R.string.spoken_overlay_permission), null)
            }
        }
        binding.homeHeader.updateDate()
        binding.homeHeader.updateWeather()

        DeviceSyncManager.getInstance(this).requestSyncNow()

        if (appPrefs.pendingCaretakerMessage.isNotBlank()) {
            startActivity(Intent(this, MessageActivity::class.java))
        }
    }

    // ── Date ─────────────────────────────────────────────────────────────────

    private fun setupDate() {
        binding.homeHeader.updateDate()
        binding.homeHeader.updateWeather()
        lifecycleScope.launch {
            val weather = com.grandma.launcher.data.WeatherRepository(this@HomeActivity).refresh()
            binding.homeHeader.showWeather(weather)
        }
    }

    private fun setupClockHold() {
        binding.homeHeader.onClockLongPress = {
            CaretakerPinDialog.show(this) {
                startActivity(Intent(this, RecordClipsActivity::class.java))
            }
        }
    }

    private fun setupHouse() {
        binding.btnHouse.setOnClickListener {
            startActivity(
                Intent(this, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    // ── Contacts ─────────────────────────────────────────────────────────────

    private fun loadContacts() {
        binding.gridContacts.removeAllViews()

        val favourites = contactRepo.getFavourites().take(MAX_HOME_CONTACTS)

        // Add contact cards
        favourites.forEach { contact ->
            val card = ContactCardView(this, contact)
            card.onTap = { showCallConfirmation(contact) }
            addCardToGrid(card)
        }
    }

    private fun addCardToGrid(view: View) {
        val cardSize = resources.getDimensionPixelSize(R.dimen.contact_card_size_home)
        val margin = resources.getDimensionPixelSize(R.dimen.space_xs)
        val cornerRadius = resources.getDimension(R.dimen.contact_card_corner_radius)

        // Wrap in MaterialCardView so the rounded corners clip the photo cleanly.
        // Without this wrapper the photo bleeds outside the rounded bg drawable.
        val card = MaterialCardView(this).apply {
            radius = cornerRadius
            cardElevation = 0f
            strokeWidth = resources.getDimensionPixelSize(R.dimen.control_border_width)
            strokeColor = ContextCompat.getColor(this@HomeActivity, R.color.color_control_border)
            setCardBackgroundColor(ContextCompat.getColor(this@HomeActivity, R.color.color_bg_card))
            useCompatPadding = false
            clipChildren = true
            clipToPadding = true
        }
        card.addView(view, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = cardSize
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(margin, margin, margin, margin)
        }
        binding.gridContacts.addView(card, params)
    }

    private fun showCallConfirmation(contact: Contact) {
        CallConfirmBottomSheet.show(supportFragmentManager, contact) {
            if (contact.whatsapp) {
                com.grandma.launcher.ui.common.WhatsAppCaller.call(this, contact) {
                    callLauncher.call(contact.phone)
                }
            } else {
                callLauncher.call(contact.phone)
            }
        }
    }

    private fun openAddContact() {
        startActivity(Intent(this, AddContactActivity::class.java))
    }

    // ── Tool buttons ─────────────────────────────────────────────────────────

    private fun setupToolButtons() {
        binding.btnCamera.setOnClickListener {
            voice.speakSlot(VoiceSlots.CAMERA, getString(R.string.spoken_camera))
            openCamera()
        }
        binding.btnWhatsapp.setOnClickListener {
            voice.speakSlot(VoiceSlots.WHATSAPP, getString(R.string.spoken_whatsapp))
            openWhatsApp()
        }
    }

    private fun openCamera() {
        val cameraPackages = listOf(
            "com.miui.camera",
            "com.android.camera",
            "com.android.camera2",
            "com.oneplus.camera",
            "com.samsung.android.camera",
            "com.google.android.GoogleCamera"
        )
        for (pkg in cameraPackages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            try {
                startActivity(intent)
                return
            } catch (_: android.content.ActivityNotFoundException) {
            }
        }
        val fallback = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        try {
            startActivity(fallback)
        } catch (_: android.content.ActivityNotFoundException) {
            com.grandma.launcher.ui.common.InstalledApp.openStore(this, "com.google.android.GoogleCamera")
        }
    }

    private fun openWhatsApp() {
        com.grandma.launcher.ui.common.InstalledApp.launchPackageOrStore(
            this,
            com.grandma.launcher.ui.common.InstalledApp.WHATSAPP
        )
    }

    // ── SOS ──────────────────────────────────────────────────────────────────

    private fun setupSosButton() {
        binding.sosButton.onSosActivated = {
            triggerSos()
        }
        if (sosCoordinator.isActive()) {
            binding.btnStopAlerts.visibility = View.VISIBLE
            binding.btnStopAlerts.setOnClickListener {
                sosCoordinator.cancel()
                binding.btnStopAlerts.visibility = View.GONE
            }
        }
    }

    private fun triggerSos() {
        // The call goes first and is never cancelled by "Stop alerts".
        voice.speakSlot(VoiceSlots.SOS, getString(R.string.spoken_sos))
        callLauncher.call(appPrefs.sosNumber, askPermission = false)
        sosCoordinator.start()
        binding.btnStopAlerts.visibility = View.VISIBLE
        binding.btnStopAlerts.setOnClickListener {
            sosCoordinator.cancel()
            binding.btnStopAlerts.visibility = View.GONE
        }
    }

    // ── Caretaker FAB ────────────────────────────────────────────────────────

    private fun setupFab() {
        CaretakerFabHelper.attach(this, binding.fabCaretaker, binding.btnHouse)
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun setupDestinations() {
        binding.homeHeader.onWeatherClick = { openWeather() }
        binding.btnYouTube.setOnClickListener {
            voice.speak(getString(R.string.spoken_youtube), null)
            InstalledApp.openYouTube(this)
        }
        binding.btnGallery.setOnClickListener {
            voice.speak(getString(R.string.spoken_gallery), null)
            InstalledApp.openGallery(this)
        }
        binding.btnAsk.setOnClickListener {
            voice.speakSlot(VoiceSlots.ASK, getString(R.string.spoken_ask))
            startActivity(Intent(this, TalkActivity::class.java))
        }
        binding.btnApps.setOnClickListener {
            voice.speakSlot(VoiceSlots.APPS, getString(R.string.spoken_apps))
            startActivity(Intent(this, MoreAppsActivity::class.java))
        }
        binding.tvSeeAll.setOnClickListener { openPeople() }
        binding.btnMorePeople.setOnClickListener { openPeople() }
    }

    private fun openPeople() {
        voice.speakSlot(VoiceSlots.PEOPLE, getString(R.string.spoken_people))
        startActivity(Intent(this, ContactsActivity::class.java))
    }

    private fun openWeather() {
        voice.speakSlot(VoiceSlots.WEATHER, getString(R.string.spoken_weather))
        startActivity(Intent(this, WeatherActivity::class.java))
    }

}
