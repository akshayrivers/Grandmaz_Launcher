package com.grandma.launcher.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.databinding.ActivityHomeBinding
import com.grandma.launcher.ui.apps.MoreAppsActivity
import com.grandma.launcher.ui.caretaker.CaretakerHelpActivity
import com.grandma.launcher.ui.contacts.AddContactActivity
import com.grandma.launcher.ui.contacts.ContactsActivity
import com.grandma.launcher.ui.contacts.CallConfirmBottomSheet
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var contactRepo: ContactRepository
    private lateinit var appPrefs: AppPreferences

    // Runnable that fades the FAB after idle period
    private val fabFadeRunnable = Runnable { fadeFabToIdle() }

    companion object {
        private const val REQUEST_CALL_PERMISSION = 1001
        private const val FAB_ACTIVE_ALPHA = 1.0f
        private const val FAB_IDLE_ALPHA = 0.25f
        private const val FAB_FADE_DURATION_MS = 400L
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

            binding.tvMoreApps.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                this.bottomMargin = systemBars.bottom
            }

            insets
        }

        ViewCompat.requestApplyInsets(binding.rootLayout)

        setupDate()
        setupFab()
        setupToolButtons()
        setupSosButton()
        setupMoreApps()
        setupSeeAll()
    }

    override fun onResume() {
        super.onResume()
        // Refresh contacts every time we return to home
        // (user may have added/removed a contact)
        loadContacts()
        if (android.provider.Settings.canDrawOverlays(this)) {
            binding.fabCaretaker.visibility = View.GONE
            try {
                startService(Intent(this, com.grandma.launcher.ui.caretaker.CaretakerFloatingService::class.java))
            } catch (_: Exception) {}
        } else {
            binding.fabCaretaker.visibility = View.VISIBLE
            restartFabIdleTimer()
        }
        binding.homeHeader.updateDate()
        binding.homeHeader.updateWeather()

        // Start background sync manager (registration, snapshot, remote tasks)
        com.grandma.launcher.remote.DeviceSyncManager.getInstance(this).startSyncLoop()
    }

    override fun onPause() {
        super.onPause()
        binding.root.removeCallbacks(fabFadeRunnable)
    }

    // ── Date ─────────────────────────────────────────────────────────────────

    private fun setupDate() {
        binding.homeHeader.updateDate()
        binding.homeHeader.updateWeather()
    }

    // ── Contacts ─────────────────────────────────────────────────────────────

    private fun loadContacts() {
        binding.gridContacts.removeAllViews()

        val favourites = contactRepo.getFavourites().take(MAX_HOME_CONTACTS)
        val slotsUsed = favourites.size
        val showAddSlot = slotsUsed < MAX_HOME_CONTACTS

        // Add contact cards
        favourites.forEach { contact ->
            val card = ContactCardView(this, contact)
            card.onTap = { showCallConfirmation(contact) }
            addCardToGrid(card)
        }

        // Add "Add Contact" slot if there's room
        if (showAddSlot) {
            val addCard = AddContactCardView(this)
            addCard.setOnClickListener { openAddContact() }
            addCardToGrid(addCard)
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
            cardElevation = resources.getDimension(R.dimen.space_sm)
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
            initiateCall(contact.phone)
        }
    }

    private fun initiateCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
            startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CALL_PERMISSION
            )
        }
    }

    private fun openAddContact() {
        startActivity(Intent(this, AddContactActivity::class.java))
    }

    // ── Tool buttons ─────────────────────────────────────────────────────────

    private fun setupToolButtons() {
        binding.btnCamera.setOnClickListener { openCamera() }
        binding.btnWhatsapp.setOnClickListener { openWhatsApp() }
    }

    private fun openCamera() {
        // Try to open the camera app directly rather than ACTION_IMAGE_CAPTURE
        // (which captures a photo for us — not what we want here).
        // We try known camera package names first, then fall back to a chooser.
        val cameraPackages = listOf(
            "com.android.camera",
            "com.android.camera2",
            "com.miui.camera",           // Xiaomi / Redmi
            "com.oneplus.camera",
            "com.samsung.android.camera",
            "com.google.android.GoogleCamera"
        )
        for (pkg in cameraPackages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                startActivity(intent)
                return
            }
        }
        // Fallback — let the system choose any camera app
        val fallback = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (fallback.resolveActivity(packageManager) != null) {
            startActivity(fallback)
        }
    }

    private fun openWhatsApp() {
        val pm = packageManager
        val whatsappPackage = "com.whatsapp"
        try {
            pm.getPackageInfo(whatsappPackage, 0)
            val intent = pm.getLaunchIntentForPackage(whatsappPackage)
            if (intent != null) startActivity(intent)
        } catch (e: PackageManager.NameNotFoundException) {
            Toast.makeText(this, R.string.whatsapp_not_installed, Toast.LENGTH_SHORT).show()
        }
    }

    // ── SOS ──────────────────────────────────────────────────────────────────

    private fun setupSosButton() {
        binding.sosButton.onSosActivated = {
            triggerSos()
        }
    }

    private fun triggerSos() {
        val emergencyNumber = appPrefs.emergencyNumber
        
        // Post high priority SOS Help Request to Fastify Backend API
        val helpRepo = com.grandma.launcher.remote.HelpRequestRepository(this)
        lifecycleScope.launchWhenStarted {
            helpRepo.createHelpRequest(
                title = "EMERGENCY SOS TRIGGERED",
                description = "User activated SOS emergency button on Grandma's Launcher",
                type = com.grandma.launcher.remote.HelpRequestRepository.HelpType.SOS
            )
        }

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$emergencyNumber"))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(intent)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CALL_PERMISSION
            )
        }
    }

    // ── Caretaker FAB ────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabCaretaker.setOnClickListener {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Enable Floating Caretaker")
                    .setMessage("Would you like to make the Caretaker icon float on top of other apps? This helps you ask for help at any time, from any app.")
                    .setPositiveButton("Enable") { _, _ ->
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("Not Now") { _, _ ->
                        startActivity(Intent(this, CaretakerHelpActivity::class.java))
                    }
                    .show()
            } else {
                startActivity(Intent(this, CaretakerHelpActivity::class.java))
                restartFabIdleTimer()
            }
        }
        // FAB starts active
        binding.fabCaretaker.alpha = FAB_ACTIVE_ALPHA
    }

    private fun restartFabIdleTimer() {
        // Cancel any pending fade
        binding.root.removeCallbacks(fabFadeRunnable)
        if (binding.fabCaretaker.visibility != View.VISIBLE) return
        // Ensure FAB is fully visible
        binding.fabCaretaker.animate()
            .alpha(FAB_ACTIVE_ALPHA)
            .setDuration(FAB_FADE_DURATION_MS / 2)
            .start()
        // Schedule fade after idle delay
        binding.root.postDelayed(fabFadeRunnable, appPrefs.fabIdleDelayMs)
    }

    private fun fadeFabToIdle() {
        if (binding.fabCaretaker.visibility != View.VISIBLE) return
        binding.fabCaretaker.animate()
            .alpha(FAB_IDLE_ALPHA)
            .setDuration(FAB_FADE_DURATION_MS)
            .start()
    }

    // Any touch on the screen reactivates the FAB
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            restartFabIdleTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun setupMoreApps() {
        binding.tvMoreApps.setOnClickListener {
            startActivity(Intent(this, MoreAppsActivity::class.java))
        }
    }

    private fun setupSeeAll() {
        binding.tvSeeAll.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Permission results are handled silently —
        // the user can retry the action by tapping again.
    }

    // ── Back / Home key ──────────────────────────────────────────────────────

    // The home screen should never be "backed out of" —
    // pressing back on home screen does nothing.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally empty — home screen is the root
    }
}
