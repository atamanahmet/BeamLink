package com.beamlink.peer.register

import android.os.Handler
import android.os.Looper
import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.net.AddressGuard
import com.beamlink.peer.security.DeviceKey
import com.beamlink.peer.security.NexusTlsPin
import com.beamlink.peer.security.PhoneTlsCert
import com.beamlink.peer.security.TokenStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Registers with nexus and owns the enrollment poll for the app process.
 */
class RegistrationCoordinator(
    private val prefs: NexusPrefs,
    private val tokenStore: TokenStore,
    private val deviceKey: DeviceKey,
    private val phoneTlsCert: PhoneTlsCert,
    private val onState: (state: String, message: String) -> Unit,
) {

    private var executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val enrollment = EnrollmentClient(prefs, tokenStore, deviceKey, phoneTlsCert) { state, message ->
        main.post { onState(state, message) }
    }
    @Volatile
    private var lastProbeUrl: String? = null
    @Volatile
    private var lastProbeFingerprint: String? = null

    /**
     * Saves and validates the nexus address.
     */
    fun setNexusAddress(raw: String) {
        val uri = AddressGuard.requireAllowedNexusUrl(raw)
        prefs.nexusUrl = uri.toString().trimEnd('/')
        if (prefs.registrationState == NexusPrefs.STATE_NOT_CONFIGURED) {
            emit(NexusPrefs.STATE_NOT_CONFIGURED, "")
        }
    }

    fun getNexusAddress(): String = prefs.nexusUrl

    /**
     * Handshake-only probe. Does not store the pin.
     */
    fun probe(raw: String): String {
        val uri = AddressGuard.requireAllowedNexusUrl(raw)
        val fingerprint = NexusTlsPin.probe(uri.host, uri.port)
        lastProbeUrl = uri.toString().trimEnd('/')
        lastProbeFingerprint = fingerprint
        prefs.nexusUrl = lastProbeUrl ?: ""
        return fingerprint
    }

    /**
     * Stores the pin after the person confirms the probed fingerprint.
     */
    fun confirm(raw: String, fingerprint: String) {
        val uri = AddressGuard.requireAllowedNexusUrl(raw)
        val url = uri.toString().trimEnd('/')
        if (lastProbeUrl != url || lastProbeFingerprint != fingerprint) {
            throw IllegalArgumentException("Confirm does not match the last Connect")
        }
        val existing = prefs.tlsPin
        if (existing.isNotBlank() && existing != fingerprint) {
            throw IllegalStateException(NexusTlsPin.MISMATCH)
        }
        prefs.tlsPin = fingerprint
        prefs.nexusUrl = url
        lastProbeUrl = null
        lastProbeFingerprint = null
    }

    /**
     * Clears the pin. Re-pin is an explicit user action.
     */
    fun clearPin() {
        prefs.tlsPin = ""
        lastProbeUrl = null
        lastProbeFingerprint = null
    }

    /**
     * Sets unregistered first-run state after local storage is cleared.
     */
    fun emitFirstRun() {
        lastProbeUrl = null
        lastProbeFingerprint = null
        emit(NexusPrefs.STATE_NOT_CONFIGURED, "")
    }

    fun hasPin(): Boolean = prefs.tlsPin.isNotBlank()

    fun pendingFingerprint(): String = lastProbeFingerprint ?: ""

    /**
     * Snapshot of registration UI state.
     */
    fun status(): Pair<String, String> {
        markUnregisteredIfTokensGone()
        return prefs.registrationState to prefs.lastError
    }

    /**
     * True when encrypted tokens load. Failed decrypt becomes unregistered.
     */
    fun hasTokens(): Boolean {
        markUnregisteredIfTokensGone()
        return tokenStore.load() != null
    }

    /**
     * Fingerprint of this device key.
     */
    fun fingerprint(): String = deviceKey.fingerprint()

    /**
     * Clears approval state when TokenStore cannot decrypt.
     */
    private fun markUnregisteredIfTokensGone() {
        val tokens = tokenStore.load()
        if (tokens != null) return
        if (prefs.registrationState == NexusPrefs.STATE_APPROVED) {
            prefs.deviceId = ""
            emit(NexusPrefs.STATE_NOT_CONFIGURED, "Tokens missing. Register again.")
        }
    }

    /**
     * Starts register then token polling.
     */
    fun register() {
        if (prefs.tlsPin.isBlank()) {
            emit(NexusPrefs.STATE_ERROR, NexusTlsPin.MISSING_PIN)
            return
        }
        enrollment.stopPoll()
        executor.execute { enrollment.registerAndPoll() }
    }

    /**
     * Clears tokens and registers again.
     */
    fun reRegister() {
        tokenStore.clear()
        prefs.deviceId = ""
        register()
    }

    /**
     * One token recovery. Does not register.
     */
    fun recoverAfterAuthLoss() {
        executor.execute { enrollment.recoverOnce() }
    }

    /**
     * Starts token polling when pending.
     */
    fun startIdentifyPoll() {
        applyPollOwner(EnrollmentPollOwnerRule.Command.START)
    }

    /**
     * Stops token polling.
     */
    fun stopIdentifyPoll() {
        applyPollOwner(EnrollmentPollOwnerRule.Command.STOP)
    }

    /**
     * Applies a start or stop command from the poll owner rule.
     */
    fun applyPollOwner(command: EnrollmentPollOwnerRule.Command) {
        when (command) {
            EnrollmentPollOwnerRule.Command.START ->
                executor.execute { enrollment.resumePollIfPending(false) }
            EnrollmentPollOwnerRule.Command.RESUME_NOW ->
                executor.execute { enrollment.resumePollIfPending(true) }
            EnrollmentPollOwnerRule.Command.STOP,
            EnrollmentPollOwnerRule.Command.PAUSE,
            -> enrollment.stopPoll()
            EnrollmentPollOwnerRule.Command.NONE -> Unit
        }
    }

    /**
     * Pauses the poll when the app goes to the background.
     */
    fun onAppBackground() {
        applyPollOwner(EnrollmentPollOwnerRule.onBackground(prefs.registrationState))
    }

    /**
     * Restarts the poll with an immediate call when the app returns.
     */
    fun onAppForeground() {
        applyPollOwner(EnrollmentPollOwnerRule.onForeground(prefs.registrationState))
    }

    /**
     * Cancels in-flight enroll HTTP, interrupts the poll thread, and waits for it.
     */
    fun cancelEnrollAndJoin() {
        enrollment.cancel()
        val old = executor
        old.shutdownNow()
        old.awaitTermination(DeviceDefaults.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        executor = Executors.newSingleThreadExecutor()
    }

    private fun emit(state: String, message: String) {
        prefs.registrationState = state
        prefs.lastError = message
        main.post { onState(state, message) }
    }
}
