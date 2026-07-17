package com.grandma.launcher.ui.contacts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.grandma.launcher.R
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.databinding.ActivityContactsBinding
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper

/**
 * Full contacts screen — photo grid, 3 per row.
 *
 * Why 3 per row (not 2 or 4)?
 * - 2 per row gives 100dp+ cards but wastes space on wider screens
 * - 4 per row makes cards too small for tremor users (~80dp)
 * - 3 per row hits the sweet spot: ~100dp cards, good density
 *
 * Tap → call confirmation
 * Long press → bottom sheet (Call / Edit / Remove)
 * Add slot always visible at end of grid
 */
class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var contactRepo: ContactRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactRepo = ContactRepository(this)

        binding.btnBack.setOnClickListener { finish() }

        CaretakerFabHelper.attach(this, binding.fabCaretaker)

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        val topMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_top)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_bottom)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.contactsRootLayout) { view, insets ->
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
        loadContacts()
    }

    private fun loadContacts() {
        binding.gridAllContacts.removeAllViews()

        val contacts = contactRepo.getAll()
        contacts.forEach { contact ->
            addContactToGrid(contact)
        }

        // "Add Contact" slot always at the end
        addAddSlotToGrid()
    }

    private fun addContactToGrid(contact: Contact) {
        val card = ContactGridItemView(this, contact)
        card.onTap = {
            CallConfirmBottomSheet.show(supportFragmentManager, contact) {
                placeCall(contact.phone)
            }
        }
        card.onLongPress = {
            ContactOptionsSheet.show(supportFragmentManager, contact,
                onEdit = {
                    val intent = Intent(this, AddContactActivity::class.java).apply {
                        putExtra(AddContactActivity.EXTRA_CONTACT_ID, contact.id)
                    }
                    startActivity(intent)
                },
                onRemove = {
                    contactRepo.remove(contact.id)
                    loadContacts()
                }
            )
        }
        addCardToGrid(card)
    }

    private fun addAddSlotToGrid() {
        val addView = AddContactGridItemView(this)
        addView.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }
        addCardToGrid(addView)
    }

    private fun addCardToGrid(view: View) {
        val size = resources.getDimensionPixelSize(R.dimen.contact_card_size_contacts)
        val margin = resources.getDimensionPixelSize(R.dimen.space_sm)
        val params = GridLayout.LayoutParams().apply {
            width = 0
            height = size
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(margin, margin, margin, margin)
        }
        binding.gridAllContacts.addView(view, params)
    }

    private fun placeCall(phone: String) {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phone"))
        startActivity(intent)
    }
}
