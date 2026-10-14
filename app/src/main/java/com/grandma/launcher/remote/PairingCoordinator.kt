package com.grandma.launcher.remote

import android.content.Context
import android.util.Log
import com.grandma.launcher.appScope
import com.grandma.launcher.data.AppPreferences
import com.grandma.launcher.network.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Primary-caretaker pairing during setup: register device → create invitation → show the
 * pairing code → poll the invitation until the caretaker accepts it on their own device.
 *
 * Runs in the application scope so leaving the setup screen does not cancel registration
 * or invitation creation. Polling only runs while the setup screen is [watch]ing.
 */
class PairingCoordinator private constructor(private val context: Context) {

    sealed class State {
        object Idle : State()
        object Sending : State()
        data class Waiting(val pairingCode: String, val expiresAtMs: Long, val connectionProblem: Boolean = false) : State()
        object Linked : State()
        /** The phone already has a primary caretaker (PRIMARY_EXISTS). */
        object AlreadyLinked : State()
        data class Failed(val reason: FailureReason) : State()
    }

    enum class FailureReason { EXPIRED, LOCKED, REVOKED, NETWORK, SERVER }

    private val appPrefs = AppPreferences(context)
    private val invitationRepo = CaretakerInvitationRepository(context)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var createJob: Job? = null
    private var pollJob: Job? = null
    private var watching = false

    /** Restores a pending invitation saved before the process died. */
    @Synchronized
    fun restore() {
        if (_state.value != State.Idle) return
        val id = appPrefs.pendingInvitationId
        if (id.isNotEmpty()) {
            _state.value = State.Waiting(appPrefs.pendingPairingCode, appPrefs.pendingPairingExpiresAtMs)
        } else if (appPrefs.isPaired) {
            _state.value = State.Linked
        }
    }

    @Synchronized
    fun startPairing(email: String) {
        pollJob?.cancel()
        createJob?.cancel()
        appPrefs.clearPendingInvitation()
        _state.value = State.Sending
        createJob = context.appScope.launch {
            when (val result = invitationRepo.createInvitation(email)) {
                is ApiClient.Result.Success -> {
                    val inv = result.data
                    appPrefs.pendingInvitationId = inv.invitationId
                    appPrefs.pendingPairingCode = inv.pairingCode
                    appPrefs.pendingPairingExpiresAtMs = inv.expiresAtMs
                    _state.value = State.Waiting(inv.pairingCode, inv.expiresAtMs)
                    startPollingIfWatching()
                }
                is ApiClient.Result.Error -> {
                    Log.e(TAG, "Invitation failed: ${result.statusCode} ${result.code}")
                    _state.value = when {
                        result.code == CaretakerInvitationRepository.CODE_PRIMARY_EXISTS -> {
                            appPrefs.isPaired = true
                            State.AlreadyLinked
                        }
                        result.isNetworkError -> State.Failed(FailureReason.NETWORK)
                        else -> State.Failed(FailureReason.SERVER)
                    }
                }
            }
        }
    }

    @Synchronized
    fun watch() {
        watching = true
        startPollingIfWatching()
    }

    @Synchronized
    fun unwatch() {
        watching = false
        pollJob?.cancel()
        pollJob = null
    }

    @Synchronized
    private fun startPollingIfWatching() {
        if (!watching || pollJob?.isActive == true) return
        if (_state.value !is State.Waiting) return
        val invitationId = appPrefs.pendingInvitationId.ifEmpty { return }
        pollJob = context.appScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (!pollOnce(invitationId)) break
            }
        }
    }

    /** Returns true while the invitation is still pending. */
    private suspend fun pollOnce(invitationId: String): Boolean {
        val waiting = _state.value as? State.Waiting ?: return false
        return when (val result = invitationRepo.getInvitationStatus(invitationId)) {
            is ApiClient.Result.Success -> when (result.data.status) {
                CaretakerInvitationRepository.STATUS_PENDING -> {
                    if (waiting.expiresAtMs in 1 until System.currentTimeMillis()) {
                        finish(State.Failed(FailureReason.EXPIRED))
                        false
                    } else {
                        _state.value = waiting.copy(connectionProblem = false)
                        true
                    }
                }
                CaretakerInvitationRepository.STATUS_ACCEPTED -> {
                    appPrefs.isPaired = true
                    finish(State.Linked)
                    DeviceSyncManager.getInstance(context).requestSyncNow(force = true)
                    false
                }
                CaretakerInvitationRepository.STATUS_LOCKED -> { finish(State.Failed(FailureReason.LOCKED)); false }
                CaretakerInvitationRepository.STATUS_REVOKED -> { finish(State.Failed(FailureReason.REVOKED)); false }
                else -> { finish(State.Failed(FailureReason.EXPIRED)); false }
            }
            is ApiClient.Result.Error -> {
                if (result.code == CaretakerInvitationRepository.CODE_INVITATION_NOT_FOUND) {
                    finish(State.Failed(FailureReason.EXPIRED))
                    false
                } else {
                    _state.value = waiting.copy(connectionProblem = true)
                    true
                }
            }
        }
    }

    private fun finish(state: State) {
        appPrefs.clearPendingInvitation()
        _state.value = state
    }

    companion object {
        private const val TAG = "PairingCoordinator"
        private const val POLL_INTERVAL_MS = 3_000L

        @Volatile
        private var INSTANCE: PairingCoordinator? = null

        fun getInstance(context: Context): PairingCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PairingCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
