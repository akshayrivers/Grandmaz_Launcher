package com.grandma.launcher.ui.contacts

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.databinding.ActivityAddContactBinding
import java.io.File
import java.io.FileOutputStream

/**
 * Add or edit a contact.
 *
 * Flow:
 *   Step 1 — Capture or pick a photo
 *   Step 2 — Enter the contact's name and phone number
 *   Step 3 — Save
 *
 * Photo storage: copied into app's private files/contact_photos/ directory.
 * This ensures the photo persists even if the original is deleted from gallery.
 * The contact record stores the absolute path to this private copy.
 *
 * Why both camera and gallery options?
 * - Camera: for adding a new contact in person (most common case)
 * - Gallery: for adding a contact from an existing photo
 *
 * The name field is typed by the caretaker/helper in Phase 1.
 * Phase 3 will add a voice recording option for non-literate caretakers.
 */
class AddContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private lateinit var contactRepo: ContactRepository

    private var capturedPhotoPath: String? = null
    private var cameraOutputUri: Uri? = null
    private var editingContactId: Long? = null

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        private const val REQUEST_CAMERA = 2001
        private const val REQUEST_GALLERY = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactRepo = ContactRepository(this)

        // Check if we're editing an existing contact
        editingContactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
            .takeIf { it != -1L }

        editingContactId?.let { loadExistingContact(it) }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnTakePhoto.setOnClickListener { launchCamera() }
        binding.btnChooseGallery.setOnClickListener { launchGallery() }
        binding.btnSaveContact.setOnClickListener { saveContact() }
    }

    private fun loadExistingContact(id: Long) {
        val contact = contactRepo.getAll().find { it.id == id } ?: return
        binding.etContactName.setText(contact.name)
        binding.etContactPhone.setText(contact.phone)
        capturedPhotoPath = contact.photoPath
        contact.photoPath?.let {
            val bmp = BitmapFactory.decodeFile(it)
            if (bmp != null) binding.ivPhotoPreview.setImageBitmap(bmp)
        }
    }

    // ── Camera ───────────────────────────────────────────────────────────────

    private fun launchCamera() {
        val photoFile = createTempPhotoFile()
        cameraOutputUri = FileProvider.getUriForFile(
            this,
            "com.grandma.launcher.fileprovider",
            photoFile
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CAMERA)
        }
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    private fun createTempPhotoFile(): File {
        val cacheDir = File(cacheDir, "camera").also { it.mkdirs() }
        return File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    }

    // ── Activity result ──────────────────────────────────────────────────────

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_CAMERA -> {
                val uri = cameraOutputUri ?: return
                processAndSavePhoto(uri)
            }
            REQUEST_GALLERY -> {
                val uri = data?.data ?: return
                processAndSavePhoto(uri)
            }
        }
    }

    /**
     * Decodes the photo, compresses it, and copies it to private storage.
     * We resize to 400×400 max to keep storage usage reasonable while
     * maintaining enough quality for the circular contact photo display.
     */
    private fun processAndSavePhoto(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Scale down if needed
            val maxSize = 400
            val scaled = if (originalBitmap.width > maxSize || originalBitmap.height > maxSize) {
                val ratio = minOf(
                    maxSize.toFloat() / originalBitmap.width,
                    maxSize.toFloat() / originalBitmap.height
                )
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (originalBitmap.width * ratio).toInt(),
                    (originalBitmap.height * ratio).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            // Save to private storage
            val photosDir = File(filesDir, "contact_photos").also { it.mkdirs() }
            val destFile = File(photosDir, "contact_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            capturedPhotoPath = destFile.absolutePath
            binding.ivPhotoPreview.setImageBitmap(scaled)

        } catch (e: Exception) {
            Toast.makeText(this, "Could not load photo", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private fun saveContact() {
        val name = binding.etContactName.text?.toString()?.trim() ?: ""
        val phone = binding.etContactPhone.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            binding.etContactName.error = "Please enter a name"
            return
        }
        if (phone.isEmpty()) {
            binding.etContactPhone.error = "Please enter a phone number"
            return
        }

        val contact = Contact(
            id = editingContactId ?: System.currentTimeMillis(),
            name = name,
            photoPath = capturedPhotoPath,
            phone = phone,
            isFavourite = true
        )

        contactRepo.save(contact)
        finish()
    }
}
