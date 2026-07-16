package com.grandma.launcher.ui.contacts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.databinding.ActivityAddContactBinding
import java.io.File
import java.io.FileOutputStream

class AddContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private lateinit var contactRepo: ContactRepository

    private var capturedPhotoPath: String? = null
    private var cameraOutputUri: Uri? = null
    private var editingContactId: Long? = null

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
    }

    // ── Modern Activity Result API — replaces deprecated onActivityResult ─────

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fireCameraIntent()
        else Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_LONG).show()
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Photo was written to cameraOutputUri by the camera app
            cameraOutputUri?.let { processAndSavePhoto(it) }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { processAndSavePhoto(it) }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactRepo = ContactRepository(this)

        editingContactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L).takeIf { it != -1L }
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

    // ── Camera ────────────────────────────────────────────────────────────────

    private fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> fireCameraIntent()
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun fireCameraIntent() {
        // Create a file in cache for the camera to write into
        val photoFile = File(cacheDir.also { it.mkdirs() }, "contact_${System.currentTimeMillis()}.jpg")
        cameraOutputUri = FileProvider.getUriForFile(
            this,
            "com.grandma.launcher.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Grant URI permission explicitly to every app that can handle this intent.
        // Required on MIUI — without this the camera app gets a SecurityException
        // trying to write to the FileProvider URI.
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .forEach { resolveInfo ->
                grantUriPermission(
                    resolveInfo.activityInfo.packageName,
                    cameraOutputUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        try {
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            galleryLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open gallery", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Photo processing ──────────────────────────────────────────────────────

    private fun processAndSavePhoto(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (original == null) {
                Toast.makeText(this, "Could not read photo", Toast.LENGTH_SHORT).show()
                return
            }

            // Scale down to 400px max — enough for the contact card, keeps storage small
            val maxSize = 400
            val scaled = if (original.width > maxSize || original.height > maxSize) {
                val ratio = minOf(maxSize.toFloat() / original.width, maxSize.toFloat() / original.height)
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * ratio).toInt(),
                    (original.height * ratio).toInt(),
                    true
                )
            } else original

            // Save to app private storage so it survives gallery deletions
            val photosDir = File(filesDir, "contact_photos").also { it.mkdirs() }
            val destFile = File(photosDir, "contact_${System.currentTimeMillis()}.jpg")
            FileOutputStream(destFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            capturedPhotoPath = destFile.absolutePath
            binding.ivPhotoPreview.setImageBitmap(scaled)

        } catch (e: Exception) {
            Toast.makeText(this, "Could not save photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

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

        contactRepo.save(Contact(
            id = editingContactId ?: System.currentTimeMillis(),
            name = name,
            photoPath = capturedPhotoPath,
            phone = phone,
            isFavourite = true
        ))
        finish()
    }
}
