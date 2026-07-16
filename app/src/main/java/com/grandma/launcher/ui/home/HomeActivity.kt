package com.grandma.launcher.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactRepo = ContactRepository(this)
        appPrefs = AppPreferences(this)

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
        restartFabIdleTimer()
    }

    override fun onPause() {
        super.onPause()
        binding.root.removeCallbacks(fabFadeRunnable)
    }

    // ── Date ─────────────────────────────────────────────────────────────────

    private fun setupDate() {
        val formatter = SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH)
        binding.tvDate.text = formatter.format(Date())
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
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = resources.getDimensionPixelSize(R.dimen.contact_card_size_home)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_xs),
                resources.getDimensionPixelSize(R.dimen.space_xs)
            )
        }
        binding.gridContacts.addView(view, params)
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
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun openWhatsApp() {
        val pm = packageManager
        val whatsappPackage = "com.whatsapp"
        return try {
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
            startActivity(Intent(this, CaretakerHelpActivity::class.java))
            restartFabIdleTimer()
        }
        // FAB starts active
        binding.fabCaretaker.alpha = FAB_ACTIVE_ALPHA
    }

    private fun restartFabIdleTimer() {
        // Cancel any pending fade
        binding.root.removeCallbacks(fabFadeRunnable)
        // Ensure FAB is fully visible
        binding.fabCaretaker.animate()
            .alpha(FAB_ACTIVE_ALPHA)
            .setDuration(FAB_FADE_DURATION_MS / 2)
            .start()
        // Schedule fade after idle delay
        binding.root.postDelayed(fabFadeRunnable, appPrefs.fabIdleDelayMs)
    }

    private fun fadeFabToIdle() {
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
