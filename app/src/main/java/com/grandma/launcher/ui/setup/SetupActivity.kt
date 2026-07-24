package com.grandma.launcher.ui.setup

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.grandma.launcher.R
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.databinding.ActivitySetupBinding
import com.grandma.launcher.ui.home.HomeActivity

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private lateinit var appPrefs: AppPreferences
    private var currentStep = 1

    // Google Sign-In launcher
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                handleGoogleSignInSuccess(account)
            } catch (e: ApiException) {
                // If standard Google Sign-In requires OAuth client ID configuration,
                // fall back gracefully to picking account email from Google accounts on device
                fallbackAccountPicker()
            }
        }

    // Account picker fallback launcher
    private val accountPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
                if (!accountName.isNull_Empty()) {
                    appPrefs.caretakerEmail = accountName
                    appPrefs.caretakerName = accountName.substringBefore("@").replace(".", " ").capitalizeWords()
                    appPrefs.caretakerGoogleId = accountName
                    updateSignedInUi(appPrefs.caretakerName, appPrefs.caretakerEmail)
                    Toast.makeText(this, "Signed in as $accountName", Toast.LENGTH_SHORT).show()
                }
            }
        }

    // RoleManager launcher for Default Launcher prompt
    private val defaultLauncherLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkDefaultLauncherStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPrefs = AppPreferences(this)

        setupStepNavigation()
        setupGoogleAuth()
        setupStep2()
        setupStep3()
        setupStep4()

        updateStepUi()
    }

    private fun setupStepNavigation() {
        binding.btnNextStep.setOnClickListener {
            if (validateCurrentStep()) {
                if (currentStep < 4) {
                    currentStep++
                    updateStepUi()
                } else {
                    completeSetup()
                }
            }
        }

        binding.btnBackStep.setOnClickListener {
            if (currentStep > 1) {
                currentStep--
                updateStepUi()
            }
        }
    }

    private fun updateStepUi() {
        binding.tvStepIndicator.text = "Step $currentStep of 4"

        binding.layoutStep1.visibility = if (currentStep == 1) View.VISIBLE else View.GONE
        binding.layoutStep2.visibility = if (currentStep == 2) View.VISIBLE else View.GONE
        binding.layoutStep3.visibility = if (currentStep == 3) View.VISIBLE else View.GONE
        binding.layoutStep4.visibility = if (currentStep == 4) View.VISIBLE else View.GONE

        binding.btnBackStep.visibility = if (currentStep > 1) View.VISIBLE else View.GONE

        binding.btnNextStep.text = if (currentStep == 4) {
            getString(R.string.setup_btn_complete)
        } else {
            getString(R.string.setup_btn_next)
        }
    }

    private fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            1 -> {
                if (appPrefs.caretakerEmail.isEmpty()) {
                    Toast.makeText(this, "Please sign in with Google or enter caretaker email", Toast.LENGTH_SHORT).show()
                    return false
                }
                true
            }
            2 -> {
                val emergencyNum = binding.etEmergencyNumber.text.toString().trim()
                val caretakerEmail = binding.etCaretakerEmail.text.toString().trim()

                if (emergencyNum.isEmpty()) {
                    binding.etEmergencyNumber.error = "Emergency number required"
                    return false
                }
                if (caretakerEmail.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(caretakerEmail).matches()) {
                    binding.etCaretakerEmail.error = "Valid caretaker email required"
                    return false
                }

                appPrefs.emergencyNumber = emergencyNum
                appPrefs.caretakerEmail = caretakerEmail
                true
            }
            3 -> {
                val pin = binding.etPin.text.toString().trim()
                val pinConfirm = binding.etPinConfirm.text.toString().trim()

                if (pin.length != 4) {
                    binding.etPin.error = "PIN must be exactly 4 digits"
                    return false
                }
                if (pin != pinConfirm) {
                    binding.etPinConfirm.error = "PINs do not match"
                    return false
                }

                appPrefs.caretakerPin = pin
                true
            }
            4 -> {
                true
            }
            else -> true
        }
    }

    // ── Google Auth ──────────────────────────────────────────────────────────

    private fun setupGoogleAuth() {
        if (appPrefs.caretakerEmail.isNotEmpty()) {
            updateSignedInUi(appPrefs.caretakerName, appPrefs.caretakerEmail)
        }

        binding.btnGoogleSignIn.setOnClickListener {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build()

            val googleSignInClient = GoogleSignIn.getClient(this, gso)
            val signInIntent = googleSignInClient.signInIntent
            try {
                googleSignInLauncher.launch(signInIntent)
            } catch (e: Exception) {
                fallbackAccountPicker()
            }
        }
    }

    private fun handleGoogleSignInSuccess(account: GoogleSignInAccount?) {
        if (account != null) {
            val email = account.email ?: ""
            val name = account.displayName ?: email.substringBefore("@")
            val id = account.id ?: email
            val photoUrl = account.photoUrl?.toString() ?: ""

            appPrefs.caretakerEmail = email
            appPrefs.caretakerName = name
            appPrefs.caretakerGoogleId = id
            appPrefs.caretakerPhotoUrl = photoUrl

            updateSignedInUi(name, email)
            Toast.makeText(this, "Signed in as $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fallbackAccountPicker() {
        try {
            val intent = android.accounts.AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null
            )
            accountPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open Google account picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSignedInUi(name: String, email: String) {
        binding.layoutSignedInState.visibility = View.VISIBLE
        binding.tvCaretakerName.text = name.ifEmpty { "Caretaker" }
        binding.tvCaretakerEmail.text = email
        binding.tvAvatarInitial.text = (name.ifEmpty { email }).firstOrNull()?.uppercase() ?: "C"

        // Also pre-fill email input in Step 2
        binding.etCaretakerEmail.setText(email)
    }

    // ── Step 2 ──────────────────────────────────────────────────────────────

    private fun setupStep2() {
        binding.etEmergencyNumber.setText(appPrefs.emergencyNumber)
        binding.etCaretakerEmail.setText(appPrefs.caretakerEmail)
    }

    // ── Step 3 ──────────────────────────────────────────────────────────────

    private fun setupStep3() {
        if (appPrefs.caretakerPin.isNotEmpty()) {
            binding.etPin.setText(appPrefs.caretakerPin)
            binding.etPinConfirm.setText(appPrefs.caretakerPin)
        }
    }

    // ── Step 4 ──────────────────────────────────────────────────────────────

    private fun setupStep4() {
        binding.btnSetDefaultLauncher.setOnClickListener {
            promptSetDefaultLauncher()
        }

        binding.btnGrantPermissions.setOnClickListener {
            requestPermissions()
        }
    }

    private fun promptSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                defaultLauncherLauncher.launch(intent)
                return
            }
        }

        // Fallback for older Android versions
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }

    private fun checkDefaultLauncherStatus() {
        Toast.makeText(this, "Launcher default status updated", Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CAMERA
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 2001)
        } else {
            Toast.makeText(this, "All permissions already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun completeSetup() {
        appPrefs.isSetupComplete = true
        Toast.makeText(this, "Setup complete! Welcome to Grandma's Launcher.", Toast.LENGTH_LONG).show()

        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}
