package com.grandma.launcher.ui.setup

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.grandma.launcher.R
import com.grandma.launcher.appScope
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.data.PinHasher
import com.grandma.launcher.data.PinManager
import com.grandma.launcher.databinding.ActivitySetupBinding
import com.grandma.launcher.remote.DeviceSyncManager
import com.grandma.launcher.remote.PairingCoordinator
import com.grandma.launcher.ui.common.PhoneCaller
import com.grandma.launcher.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-time setup by a family helper (English text is fine here; grandma never uses it):
 * language → main caretaker email → pairing code (wait for acceptance) → PIN →
 * home screen role + permissions.
 *
 * Registration and invitation creation run in the application scope
 * ([PairingCoordinator]), so leaving this screen never cancels them.
 */
class SetupActivity : AppCompatActivity() {

    private enum class Step { LANGUAGE, EMAIL, PAIRING, PIN, PERMISSIONS }

    private lateinit var binding: ActivitySetupBinding
    private lateinit var appPrefs: AppPreferences
    private lateinit var pairing: PairingCoordinator
    private var step = Step.LANGUAGE
    private var pairingDone = false
    private var savingPin = false

    private val defaultLauncherLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPermissionStatus()
        }

    private val callPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            callPermissionAsked = true
            refreshPermissionStatus()
            if (!granted) binding.tvCallPermissionStatus.requestFocus()
        }

    private val optionalPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionStatus()
        }

    private var callPermissionAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPrefs = AppPreferences(this)
        pairing = PairingCoordinator.getInstance(this)
        pairing.restore()

        val horizontal = resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal)
        ViewCompat.setOnApplyWindowInsetsListener(binding.setupRootLayout) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(horizontal, bars.top, horizontal, bars.bottom)
            insets
        }

        step = savedInstanceState?.getString(STATE_STEP)?.let { Step.valueOf(it) }
            ?: if (appPrefs.pendingInvitationId.isNotEmpty()) Step.PAIRING else Step.LANGUAGE
        callPermissionAsked = savedInstanceState?.getBoolean(STATE_CALL_ASKED) ?: false

        setupLanguageStep()
        setupEmailStep()
        setupPairingStep()
        setupPinStep()
        setupPermissionsStep()
        setupNavigation()

        lifecycleScope.launch {
            pairing.state.collect { renderPairing(it) }
        }

        showStep(step)
    }

    override fun onStart() {
        super.onStart()
        pairing.watch()
    }

    override fun onStop() {
        pairing.unwatch()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_STEP, step.name)
        outState.putBoolean(STATE_CALL_ASKED, callPermissionAsked)
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private fun setupNavigation() {
        binding.btnNextStep.setOnClickListener { onPrimaryAction() }
        binding.btnBackStep.setOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (step == Step.LANGUAGE) {
                    // First-time setup must not drop back to an unconfigured launcher.
                    if (appPrefs.isSetupComplete) finish()
                } else {
                    goBack()
                }
            }
        })
    }

    private fun goBack() {
        val previous = Step.values().getOrNull(step.ordinal - 1) ?: return
        showStep(previous)
    }

    private fun showStep(target: Step) {
        step = target
        binding.tvStepIndicator.text = getString(R.string.setup_step_indicator, target.ordinal + 1, Step.values().size)
        binding.layoutStepLanguage.visibility = visibleIf(target == Step.LANGUAGE)
        binding.layoutStepEmail.visibility = visibleIf(target == Step.EMAIL)
        binding.layoutStepPairing.visibility = visibleIf(target == Step.PAIRING)
        binding.layoutStepPin.visibility = visibleIf(target == Step.PIN)
        binding.layoutStepPermissions.visibility = visibleIf(target == Step.PERMISSIONS)

        binding.btnBackStep.visibility = visibleIf(target != Step.LANGUAGE)
        binding.btnNextStep.visibility = visibleIf(target != Step.LANGUAGE)
        when (target) {
            Step.LANGUAGE -> Unit
            Step.EMAIL -> updateEmailButton()
            Step.PAIRING -> {
                binding.btnNextStep.setText(R.string.setup_btn_next)
                setNextEnabled(pairingDone)
            }
            Step.PIN -> {
                binding.btnNextStep.setText(R.string.setup_btn_save_pin)
                setNextEnabled(!savingPin)
            }
            Step.PERMISSIONS -> {
                binding.btnNextStep.setText(R.string.setup_btn_complete)
                setNextEnabled(true)
                refreshPermissionStatus()
            }
        }
    }

    private fun onPrimaryAction() {
        when (step) {
            Step.LANGUAGE -> Unit
            Step.EMAIL -> submitEmail()
            Step.PAIRING -> if (pairingDone) showStep(Step.PIN)
            Step.PIN -> submitPin()
            Step.PERMISSIONS -> completeSetup()
        }
    }

    private fun setNextEnabled(enabled: Boolean) {
        binding.btnNextStep.isEnabled = enabled
        binding.btnNextStep.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    // ── Step: language ───────────────────────────────────────────────────────

    private fun setupLanguageStep() {
        binding.btnLanguageHindi.setOnClickListener { chooseLanguage(AppPreferences.LANGUAGE_HINDI) }
        binding.btnLanguageEnglish.setOnClickListener { chooseLanguage(AppPreferences.LANGUAGE_ENGLISH) }
    }

    private fun chooseLanguage(language: String) {
        appPrefs.language = language
        showStep(Step.EMAIL)
    }

    // ── Step: caretaker email ────────────────────────────────────────────────

    private fun setupEmailStep() {
        binding.etCaretakerEmail.setText(appPrefs.caretakerEmail)
        binding.etCaretakerEmail.doAfterTextChanged {
            binding.tilCaretakerEmail.error = null
            updateEmailButton()
        }
        binding.etCaretakerEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitEmail()
                true
            } else {
                false
            }
        }
    }

    private fun updateEmailButton() {
        val keepExisting = appPrefs.isPaired && binding.etCaretakerEmail.text.isNullOrBlank()
        binding.tvEmailAlreadyLinked.visibility = visibleIf(appPrefs.isPaired)
        binding.btnNextStep.setText(if (keepExisting) R.string.setup_btn_keep_caretaker else R.string.setup_btn_send_invitation)
        setNextEnabled(true)
    }

    private fun submitEmail() {
        val email = binding.etCaretakerEmail.text?.toString()?.trim().orEmpty()
        if (email.isEmpty() && appPrefs.isPaired) {
            showStep(Step.PIN)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilCaretakerEmail.error = getString(R.string.setup_email_invalid)
            return
        }
        appPrefs.setPrimaryCaretakerEmail(email)
        pairing.startPairing(email)
        showStep(Step.PAIRING)
    }

    // ── Step: pairing code ───────────────────────────────────────────────────

    private fun setupPairingStep() {
        binding.btnPairingRetry.setOnClickListener {
            val email = appPrefs.caretakerEmail
            if (email.isBlank()) showStep(Step.EMAIL) else pairing.startPairing(email)
        }
    }

    private fun renderPairing(state: PairingCoordinator.State) {
        val b = binding
        var code: String? = null
        var busy = false
        var retry = false
        val statusRes: Int
        var statusColor = R.color.color_text_primary
        pairingDone = false

        when (state) {
            PairingCoordinator.State.Idle -> {
                // e.g. the process died while the invitation was being created
                statusRes = R.string.setup_pairing_idle
                retry = true
            }
            PairingCoordinator.State.Sending -> {
                statusRes = R.string.setup_pairing_sending
                busy = true
            }
            is PairingCoordinator.State.Waiting -> {
                code = formatCode(state.pairingCode)
                busy = true
                if (state.connectionProblem) {
                    statusRes = R.string.setup_pairing_connection_problem
                    statusColor = R.color.color_accent_amber
                } else {
                    statusRes = R.string.setup_pairing_waiting
                }
            }
            PairingCoordinator.State.Linked -> {
                statusRes = R.string.setup_pairing_linked
                statusColor = R.color.color_accent_green
                pairingDone = true
            }
            PairingCoordinator.State.AlreadyLinked -> {
                statusRes = R.string.setup_pairing_already_linked
                statusColor = R.color.color_accent_green
                pairingDone = true
            }
            is PairingCoordinator.State.Failed -> {
                retry = true
                statusColor = R.color.color_accent_amber
                statusRes = when (state.reason) {
                    PairingCoordinator.FailureReason.EXPIRED -> R.string.setup_pairing_expired
                    PairingCoordinator.FailureReason.LOCKED -> R.string.setup_pairing_locked
                    PairingCoordinator.FailureReason.REVOKED -> R.string.setup_pairing_revoked
                    PairingCoordinator.FailureReason.NETWORK -> R.string.setup_pairing_network
                    PairingCoordinator.FailureReason.SERVER -> R.string.setup_pairing_server
                }
            }
        }

        b.tvPairingCode.visibility = visibleIf(code != null)
        b.tvPairingCode.text = code.orEmpty()
        b.progressPairing.visibility = visibleIf(busy)
        b.btnPairingRetry.visibility = visibleIf(retry)
        b.tvPairingStatus.setText(statusRes)
        b.tvPairingStatus.setTextColor(ContextCompat.getColor(this, statusColor))
        if (step == Step.PAIRING) setNextEnabled(pairingDone)
    }

    private fun formatCode(code: String): String =
        if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code

    // ── Step: PIN ────────────────────────────────────────────────────────────

    private fun setupPinStep() {
        // Never prefilled: the stored value is a hash and the PIN must be chosen fresh.
        binding.etPin.doAfterTextChanged { binding.tilPin.error = null }
        binding.etPinConfirm.doAfterTextChanged { binding.tilPinConfirm.error = null }
        binding.etPinConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitPin()
                true
            } else {
                false
            }
        }
    }

    private fun submitPin() {
        if (savingPin) return
        val pin = binding.etPin.text?.toString().orEmpty()
        val confirm = binding.etPinConfirm.text?.toString().orEmpty()
        if (!PinHasher.isValidFormat(pin)) {
            binding.tilPin.error = getString(R.string.setup_pin_invalid)
            return
        }
        if (pin != confirm) {
            binding.tilPinConfirm.error = getString(R.string.setup_pin_mismatch)
            return
        }
        savingPin = true
        setNextEnabled(false)
        val pinManager = PinManager(appPrefs)
        lifecycleScope.launch {
            withContext(Dispatchers.Default) { pinManager.setPin(pin) }
            savingPin = false
            binding.etPin.text = null
            binding.etPinConfirm.text = null
            showStep(Step.PERMISSIONS)
        }
    }

    // ── Step: home screen + permissions ──────────────────────────────────────

    private fun setupPermissionsStep() {
        binding.btnSetDefaultLauncher.setOnClickListener { promptSetDefaultLauncher() }
        binding.btnGrantCallPermission.setOnClickListener { requestCallPermission() }
        binding.btnGrantOptionalPermissions.setOnClickListener {
            val optional = mutableListOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                optional += Manifest.permission.POST_NOTIFICATIONS
            }
            optionalPermissionsLauncher.launch(optional.toTypedArray())
        }
    }

    private fun promptSetDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    defaultLauncherLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                } catch (_: Exception) {
                }
            }
        }
        try {
            defaultLauncherLauncher.launch(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            defaultLauncherLauncher.launch(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestCallPermission() {
        if (PhoneCaller.hasCallPermission(this)) {
            refreshPermissionStatus()
            return
        }
        if (isCallPermissionPermanentlyDenied()) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun isCallPermissionPermanentlyDenied(): Boolean =
        callPermissionAsked && !PhoneCaller.hasCallPermission(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)

    private fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    }

    private fun refreshPermissionStatus() {
        if (!::binding.isInitialized) return
        val isHome = isDefaultLauncher()
        binding.tvLauncherStatus.setText(if (isHome) R.string.setup_launcher_is_default else R.string.setup_launcher_not_default)
        binding.tvLauncherStatus.setTextColor(
            ContextCompat.getColor(this, if (isHome) R.color.color_accent_green else R.color.color_text_secondary)
        )

        val canCall = PhoneCaller.hasCallPermission(this)
        binding.tvCallPermissionStatus.setText(
            when {
                canCall -> R.string.setup_call_permission_granted
                isCallPermissionPermanentlyDenied() -> R.string.setup_call_permission_settings
                else -> R.string.setup_call_permission_needed
            }
        )
        binding.tvCallPermissionStatus.setTextColor(
            ContextCompat.getColor(this, if (canCall) R.color.color_accent_green else R.color.color_accent_amber)
        )
    }

    private fun completeSetup() {
        if (!PhoneCaller.hasCallPermission(this)) {
            refreshPermissionStatus()
            requestCallPermission()
            return
        }
        appPrefs.isSetupComplete = true
        val sync = DeviceSyncManager.getInstance(this)
        appScope.launch {
            sync.schedulePeriodicSync()
            sync.requestSyncNow(force = true)
        }

        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun visibleIf(condition: Boolean) = if (condition) View.VISIBLE else View.GONE

    private companion object {
        const val STATE_STEP = "setup_step"
        const val STATE_CALL_ASKED = "call_permission_asked"
        const val DISABLED_ALPHA = 0.4f
    }
}
