package com.grandma.launcher.ui.contacts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.data.Contact
import com.grandma.launcher.data.ContactPhone
import com.grandma.launcher.data.ContactRepository
import com.grandma.launcher.databinding.ActivityAddContactBinding
import com.grandma.launcher.ui.caretaker.CaretakerFabHelper
import com.grandma.launcher.network.ApiClient
import com.grandma.launcher.remote.DeviceRegistrationRepository
import com.grandma.launcher.voice.ClipRecorder
import com.grandma.launcher.voice.VoiceManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AddContactActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddContactBinding
    private lateinit var contactRepo: ContactRepository

    private var capturedPhotoPath: String? = null
    private var cameraOutputUri: Uri? = null
    private var editingContactId: Long? = null
    private var importedPhones: List<ContactPhone> = emptyList()
    private var nameClipId: String? = null
    private var recorder: ClipRecorder? = null
    private val voice by lazy { VoiceManager(this) }
    private var saidNamePrompt = false

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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityAddContactBinding.inflate(layoutInflater)
        setContentView(binding.root)

        contactRepo = ContactRepository(this)

        editingContactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L).takeIf { it != -1L }
        editingContactId?.let { loadExistingContact(it) } ?: showPhotoStep()

        binding.btnBack.setOnClickListener { onBackPressedStep() }
        CaretakerFabHelper.attach(this, binding.fabCaretaker)
        binding.btnTakePhoto.setOnClickListener { launchCamera() }
        binding.btnChooseGallery.setOnClickListener { launchGallery() }
        binding.btnImportContact.setOnClickListener { launchImport() }
        binding.btnRecordName.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> beginRecording()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    endRecording()
                    view.performClick()
                }
            }
            true
        }
        binding.btnSaveContact.setOnClickListener { saveContact() }
        binding.etContactName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshSave()
        })

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        val topMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_top)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_bottom)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.addContactRootLayout) { view, insets ->
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

    private fun loadExistingContact(id: Long) {
        val contact = contactRepo.getAll().find { it.id == id } ?: return
        binding.etContactName.setText(contact.name)
        binding.etContactPhone.setText(contact.phone)
        binding.cbWhatsapp.isChecked = contact.whatsapp
        importedPhones = contact.phones
        nameClipId = contact.nameClipId
        capturedPhotoPath = contact.photoPath
        contact.photoPath?.let {
            val bmp = BitmapFactory.decodeFile(it)
            if (bmp != null) showPhoto(bmp)
        }
        showVoiceStep(speakPrompt = false)
    }

    private fun showPhotoStep() {
        binding.groupVoice.visibility = View.GONE
        binding.btnTakePhoto.visibility = View.VISIBLE
        binding.btnChooseGallery.visibility = View.VISIBLE
    }

    private fun showVoiceStep(speakPrompt: Boolean) {
        binding.btnTakePhoto.visibility = View.GONE
        binding.btnChooseGallery.visibility = View.GONE
        binding.groupVoice.visibility = View.VISIBLE
        refreshSave()
        if (speakPrompt && !saidNamePrompt) {
            saidNamePrompt = true
            voice.speak(getString(R.string.spoken_say_name), null)
        }
    }

    private fun onBackPressedStep() {
        if (editingContactId == null && binding.groupVoice.visibility == View.VISIBLE) {
            showPhotoStep()
        } else {
            finish()
        }
    }

    private fun refreshSave() {
        val ready = editingContactId != null ||
            !nameClipId.isNullOrBlank() ||
            !binding.etContactName.text.isNullOrBlank()
        binding.btnSaveContact.visibility = if (ready) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        recorder?.cancel()
        recorder = null
        voice.shutdown()
        super.onDestroy()
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        readImportedContact(uri)
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) fireImport()
    }

    private fun launchImport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            fireImport()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun fireImport() {
        importLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }

    private fun readImportedContact(uri: Uri) {
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            ),
            null, null, null
        ) ?: return
        cursor.use {
            if (!it.moveToFirst()) return
            val name = it.getString(0).orEmpty()
            val number = it.getString(1).orEmpty()
            val contactId = it.getString(2)
            if (name.isNotBlank()) binding.etContactName.setText(name)
            binding.etContactPhone.setText(number)
            importedPhones = loadAllNumbers(contactId).ifEmpty {
                listOf(ContactPhone(id = "pimport", label = "", e164 = number.filter { ch -> ch == '+' || ch.isDigit() }, whatsapp = false, isDefault = true))
            }
        }
    }

    private fun loadAllNumbers(contactId: String?): List<ContactPhone> {
        if (contactId.isNullOrBlank()) return emptyList()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        ) ?: return emptyList()
        return cursor.use {
            val phones = mutableListOf<ContactPhone>()
            var index = 0
            while (it.moveToNext()) {
                val raw = it.getString(0).orEmpty()
                val e164 = raw.filter { ch -> ch == '+' || ch.isDigit() }
                if (e164.isBlank()) continue
                phones += ContactPhone(
                    id = "p$index",
                    label = "",
                    e164 = e164,
                    whatsapp = false,
                    isDefault = index == 0
                )
                index++
            }
            phones
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
            showPhoto(scaled)
            showVoiceStep(speakPrompt = editingContactId == null)

        } catch (e: Exception) {
            Toast.makeText(this, "Could not save photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun showPhoto(bitmap: Bitmap) {
        binding.ivPhotoPreview.setImageBitmap(bitmap)
        binding.ivPhotoPreview.setPadding(0, 0, 0, 0)
        binding.ivPhotoPreview.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private fun beginRecording() {
        if (recorder != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val file = File(filesDir, "voice/recording.m4a")
        val next = ClipRecorder(file, DeviceRegistrationRepository(this))
        try {
            next.start()
        } catch (_: Exception) {
            return
        }
        recorder = next
        binding.btnRecordName.setBackgroundColor(Color.WHITE)
        binding.btnRecordName.imageTintList = ColorStateList.valueOf(Color.BLACK)
        pulse()
    }

    private fun endRecording() {
        binding.btnRecordName.setBackgroundResource(R.drawable.bg_control)
        binding.btnRecordName.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.color_text_primary)
        )
        val active = recorder ?: return
        recorder = null
        binding.btnRecordName.isEnabled = false
        lifecycleScope.launch {
            when (val result = active.stopAndUpload()) {
                is ApiClient.Result.Success -> keepClip(result.data)
                is ApiClient.Result.Error -> if (!keepLocalClip()) pulse()
            }
            binding.btnRecordName.isEnabled = true
        }
    }

    private fun keepClip(id: String) {
        nameClipId = id
        refreshSave()
        voice.speak(binding.etContactName.text?.toString().orEmpty().ifBlank { " " }, id)
    }

    private fun keepLocalClip(): Boolean {
        val source = File(filesDir, "voice/recording.m4a")
        if (!source.exists()) return false
        val id = "local-${System.currentTimeMillis()}"
        val stored = File(filesDir, "voice/$id.m4a")
        return source.renameTo(stored).also { moved -> if (moved) keepClip(id) }
    }

    private fun pulse() {
        getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun saveContact() {
        val name = binding.etContactName.text?.toString()?.trim().orEmpty()
        val phone = binding.etContactPhone.text?.toString()?.trim().orEmpty()

        if (name.isEmpty() && nameClipId.isNullOrBlank()) return
        if (capturedPhotoPath == null && editingContactId == null) return

        val id = editingContactId ?: System.currentTimeMillis()
        val phones = when {
            phone.isEmpty() && importedPhones.isEmpty() -> emptyList()
            importedPhones.isNotEmpty() -> importedPhones.map { it.copy(whatsapp = it.whatsapp || binding.cbWhatsapp.isChecked) }
            else -> listOf(ContactPhone(id = "p$id", label = "", e164 = phone, whatsapp = binding.cbWhatsapp.isChecked, isDefault = true))
        }
        contactRepo.save(Contact(
            id = id,
            name = name,
            photoPath = capturedPhotoPath,
            phone = phones.firstOrNull { it.isDefault }?.e164 ?: phone,
            isFavourite = true,
            phones = phones,
            whatsapp = phones.any { it.whatsapp },
            nameClipId = nameClipId
        ))
        com.grandma.launcher.remote.DeviceConfigRepository(this).noteLocalEdits()
        com.grandma.launcher.remote.DeviceSyncManager.getInstance(this).requestSyncNow(force = true)
        finish()
    }
}
