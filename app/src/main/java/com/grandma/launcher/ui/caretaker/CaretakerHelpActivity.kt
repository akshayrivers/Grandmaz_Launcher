package com.grandma.launcher.ui.caretaker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.ActivityCaretakerHelpBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Caretaker help request screen.
 *
 * The user taps the amber FAB → arrives here.
 * They (or a helper nearby) can add an optional note.
 * Tapping "Send" fires a mailto: Intent which opens the
 * device's email app pre-filled with the caretaker's address.
 *
 * Phase 1: mailto: Intent — simple, no backend required.
 * Phase 2: This screen will call an API endpoint instead,
 *           enabling push notifications to the caretaker's PWA.
 *           The UI will not need to change — only the send action.
 *
 * Why mailto: and not a direct send?
 * - No backend, no API keys, no server costs in Phase 1
 * - The caretaker's email provider handles delivery
 * - The user (or nearby helper) sees the email being sent
 *   which provides reassurance that it worked
 */
class CaretakerHelpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaretakerHelpBinding
    private lateinit var appPrefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityCaretakerHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPrefs = AppPreferences(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSendHelp.setOnClickListener { sendHelpRequest() }
        binding.btnCancelHelp.setOnClickListener { finish() }

        CaretakerFabHelper.attach(this, binding.fabCaretaker)

        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        val topMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_top)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.screen_margin_bottom)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.caretakerHelpRootLayout) { view, insets ->
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

    private fun sendHelpRequest() {
        val caretakerEmail = appPrefs.caretakerEmail

        if (caretakerEmail.isBlank()) {
            Toast.makeText(
                this,
                "No caretaker email configured. Please ask your helper to set one up.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val note = binding.etNote.text?.toString()?.trim()
            .takeIf { !it.isNullOrBlank() }
            ?: getString(R.string.caretaker_no_note)

        val timestamp = SimpleDateFormat(
            "dd MMM yyyy, hh:mm a", Locale.ENGLISH
        ).format(Date())

        val subject = getString(R.string.caretaker_email_subject)
        val body = getString(R.string.caretaker_email_body, timestamp, note)

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(caretakerEmail))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                "No email app found on this phone",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
