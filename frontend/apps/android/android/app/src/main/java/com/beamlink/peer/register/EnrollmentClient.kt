package com.beamlink.peer.register

import android.util.Log
import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.net.AddressGuard
import com.beamlink.peer.net.HttpJsonClient
import com.beamlink.peer.security.DeviceKey
import com.beamlink.peer.security.DeviceSignaturePayload
import com.beamlink.peer.security.NexusTlsPin
import com.beamlink.peer.security.PhoneTlsCert
import com.beamlink.peer.security.TokenStore
import com.beamlink.peer.util.WifiIpv4
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Register, challenge and token poll. Tokens go to TokenStore.
 */
class EnrollmentClient(
    private val prefs: NexusPrefs,
    private val tokenStore: TokenStore,
    private val deviceKey: DeviceKey,
    private val phoneTlsCert: PhoneTlsCert,
    private val http: HttpJsonClient = HttpJsonClient(),
    private val onState: (state: String, message: String) -> Unit,
) {

    private val polling = AtomicBoolean(false)
    @Volatile
    private var lastRecoveryMs = 0L
    @Volatile
    private var pollBase = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS
    @Volatile
    private var pollSticky = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS
    @Volatile
    private var pollFailures = 0
    @Volatile
    private var pollSleep = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS

    /**
     * Register with the device public key then poll /token.
     */
    fun registerAndPoll() {
        val nexusUrl = prefs.nexusUrl
        if (nexusUrl.isBlank()) {
            emit(NexusPrefs.STATE_ERROR, "Nexus address is not configured")
            return
        }
        emit(NexusPrefs.STATE_REGISTERING, "")
        try {
            if (prefs.tlsPin.isBlank()) {
                emit(NexusPrefs.STATE_ERROR, NexusTlsPin.MISSING_PIN)
                return
            }
            AddressGuard.requireAllowedNexusUrl(nexusUrl)
            val ip = WifiIpv4.resolve()
            AddressGuard.requireAllowedHost(ip)
            val name =
                "Android-${android.os.Build.MODEL}-${deviceKey.fingerprint().take(4)}"
            phoneTlsCert.loadOrCreate()
            val publicKey = deviceKey.publicKeyBase64()
            val tlsFingerprint = phoneTlsCert.fingerprint()
            val tlsPort = DeviceDefaults.DEVICE_PORT
            val signature = deviceKey.sign(
                DeviceSignaturePayload.signedRegisterMessage(publicKey, tlsFingerprint, tlsPort)
                    .toByteArray(Charsets.UTF_8),
            )
            val body = JSONObject()
                .put("deviceName", name)
                .put("ipAddress", ip)
                .put("port", DeviceDefaults.DEVICE_PORT)
                .put("publicKey", publicKey)
                .put("tlsFingerprint", tlsFingerprint)
                .put("tlsPort", tlsPort)
                .put("signature", signature)
            val res = http.postJson(
                "${nexusUrl.trimEnd('/')}/api/devices/register",
                body,
                timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
            )
            val json = parse(res.body)
            if (res.status !in 200..299 || !json.has("deviceId")) {
                emit(NexusPrefs.STATE_ERROR, json.optString("error", "Register failed"))
                return
            }
            prefs.registeredIp = ip
            prefs.deviceId = json.getString("deviceId")
            val interval = json.optInt("interval", EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS)
            emit(NexusPrefs.STATE_PENDING, "Waiting for admin approval")
            pollUntilSettled(json.getString("deviceId"), interval.coerceAtLeast(1))
        } catch (e: Exception) {
            emit(NexusPrefs.STATE_ERROR, e.message ?: "Registration failed")
        }
    }

    /**
     * One challenge and token when local APPROVED lost access. Not a register.
     */
    fun recoverOnce() {
        if (!RecoveryGate.canRecover(
                prefs.registrationState == NexusPrefs.STATE_APPROVED && tokenStore.load() != null,
                System.currentTimeMillis(),
                lastRecoveryMs,
            )
        ) {
            return
        }
        val deviceId = prefs.deviceId
        if (deviceId.isBlank() || prefs.nexusUrl.isBlank() || prefs.tlsPin.isBlank()) {
            return
        }
        lastRecoveryMs = System.currentTimeMillis()
        val call = challengeAndToken(prefs.nexusUrl.trimEnd('/'), deviceId)
        val action = applyTokenCall(call, false)
        if (action == EnrollmentPollRule.Action.KEEP_PENDING) {
            emit(NexusPrefs.STATE_PENDING, "Waiting for admin approval")
            pollUntilSettled(
                deviceId,
                if (call.interval > 0) call.interval else EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS,
            )
        }
    }

    /**
     * Resume token poll when local state is pending. Keeps the last interval.
     */
    fun resumePollIfPending(immediate: Boolean = false) {
        if (prefs.registrationState != NexusPrefs.STATE_PENDING) {
            return
        }
        val deviceId = prefs.deviceId
        if (deviceId.isBlank() || prefs.tlsPin.isBlank()) {
            return
        }
        pollUntilSettled(deviceId, null, immediate)
    }

    fun stopPoll() {
        polling.set(false)
    }

    /**
     * Stops the poll loop and aborts the in-flight HTTP call.
     */
    fun cancel() {
        polling.set(false)
        http.cancelActiveCall()
    }

    /**
     * Polls /token until tokens, reject, or stop. Null start keeps last wait.
     */
    private fun pollUntilSettled(
        deviceId: String,
        startInterval: Int?,
        skipFirstSleep: Boolean = false,
    ) {
        if (!polling.compareAndSet(false, true)) {
            return
        }
        if (startInterval != null) {
            val baseInterval = startInterval.coerceAtLeast(1)
            pollBase = baseInterval
            pollSticky = baseInterval
            pollFailures = 0
            pollSleep = baseInterval
        }
        var grantRetried = false
        var skipSleep = skipFirstSleep
        try {
            val base = prefs.nexusUrl.trimEnd('/')
            while (polling.get()) {
                if (!skipSleep) {
                    try {
                        Thread.sleep(pollSleep.coerceAtLeast(0) * 1000L)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
                skipSleep = false
                if (!polling.get()) {
                    break
                }
                val call = challengeAndToken(base, deviceId)
                val action = applyTokenCall(call, grantRetried)
                when (action) {
                    EnrollmentPollRule.Action.RETRY_CHALLENGE_ONCE -> {
                        grantRetried = true
                    }
                    EnrollmentPollRule.Action.KEEP_PENDING -> {
                        val wait = EnrollmentPollRule.nextWait(
                            pollBase,
                            pollSticky,
                            pollFailures,
                            call.status,
                            call.error,
                        ) { bound -> ThreadLocalRandom.current().nextInt(bound) }
                        pollSticky = wait.stickySeconds
                        pollFailures = wait.failureCount
                        pollSleep = wait.sleepSeconds
                        grantRetried = false
                    }
                    else -> return
                }
            }
        } finally {
            polling.set(false)
        }
    }

    private fun applyTokenCall(call: TokenCall, grantRetried: Boolean): EnrollmentPollRule.Action {
        val action = if (
            call.ok && call.authToken != null && call.publicToken != null && call.deviceId != null
        ) {
            tokenStore.save(
                TokenStore.Tokens(
                    deviceId = call.deviceId,
                    publicId = call.publicId ?: "",
                    authToken = call.authToken,
                    publicToken = call.publicToken,
                    deviceName = call.approvedName ?: "",
                    nexusPublicKey = call.nexusPublicKey,
                ),
            )
            prefs.deviceId = call.deviceId
            emit(NexusPrefs.STATE_APPROVED, "")
            EnrollmentPollRule.Action.STORE_TOKENS
        } else {
            val mapped = EnrollmentPollRule.onError(call.status, call.error, grantRetried)
            when (mapped) {
                EnrollmentPollRule.Action.SET_REJECTED_STOP -> {
                    tokenStore.clear()
                    emit(NexusPrefs.STATE_REJECTED, "REJECTED")
                }
                EnrollmentPollRule.Action.KEEP_PENDING -> {
                    emit(NexusPrefs.STATE_PENDING, "Waiting for admin approval")
                }
                EnrollmentPollRule.Action.STOP -> {
                    emit(NexusPrefs.STATE_ERROR, call.error ?: "Enrollment stopped")
                }
                else -> Unit
            }
            mapped
        }
        Log.i(
            TAG,
            "poll action=${action.name} status=${call.status} error=${call.error ?: "-"}",
        )
        return action
    }

    private fun challengeAndToken(base: String, deviceId: String): TokenCall {
        val timeout = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt()
        return try {
            val ch = http.postJson(
                "$base/api/devices/challenge",
                JSONObject().put("deviceId", deviceId),
                timeoutMs = timeout,
            )
            val chJson = parse(ch.body)
            val challenge = chJson.optString("challenge", "")
            if (challenge.isBlank()) {
                return TokenCall(
                    false,
                    ch.status,
                    errorField(chJson),
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                )
            }
            phoneTlsCert.loadOrCreate()
            val tlsFingerprint = phoneTlsCert.fingerprint()
            val tlsPort = DeviceDefaults.DEVICE_PORT
            val storedPin = prefs.tlsPin
            if (storedPin.isBlank()) {
                return TokenCall(false, null, null, 0, null, null, null, null, null, null)
            }
            val nexusLeaf = storedPin.replace(" ", "").lowercase(Locale.ROOT)
            val signature = deviceKey.sign(
                DeviceSignaturePayload.signedMessage(
                    deviceId,
                    challenge,
                    tlsFingerprint,
                    tlsPort,
                    nexusLeaf,
                ).toByteArray(Charsets.UTF_8),
            )
            val tok = http.postJson(
                "$base/api/devices/token",
                JSONObject()
                    .put("deviceId", deviceId)
                    .put("challenge", challenge)
                    .put("signature", signature)
                    .put("tlsFingerprint", tlsFingerprint)
                    .put("tlsPort", tlsPort)
                    .put("nexusLeafSha256", nexusLeaf),
                timeoutMs = timeout,
            )
            val json = parse(tok.body)
            if (json.has("authToken")) {
                return TokenCall(
                    true,
                    tok.status,
                    null,
                    0,
                    json.optString("deviceId", deviceId),
                    json.optString("approvedName").ifEmpty { null },
                    json.optString("authToken").ifEmpty { null },
                    json.optString("publicToken").ifEmpty { null },
                    json.optString("publicId").ifEmpty { null },
                    json.optString("nexusPublicKey").ifEmpty { null },
                )
            }
            TokenCall(
                false,
                tok.status,
                errorField(json),
                json.optInt("interval", 0),
                null,
                null,
                null,
                null,
                null,
                null,
            )
        } catch (_: Exception) {
            TokenCall(false, null, null, 0, null, null, null, null, null, null)
        }
    }

    /**
     * Reads error when the JSON field is present and not blank.
     */
    private fun errorField(json: JSONObject): String? {
        if (!json.has("error") || json.isNull("error")) {
            return null
        }
        return json.optString("error").ifBlank { null }
    }

    private fun parse(body: String): JSONObject {
        return try {
            HttpJsonClient.parseObject(body)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun emit(state: String, message: String) {
        prefs.registrationState = state
        prefs.lastError = message
        onState(state, message)
    }

    companion object {
        private const val TAG = "EnrollmentClient"
    }

    private data class TokenCall(
        val ok: Boolean,
        val status: Int?,
        val error: String?,
        val interval: Int,
        val deviceId: String?,
        val approvedName: String?,
        val authToken: String?,
        val publicToken: String?,
        val publicId: String?,
        val nexusPublicKey: String?,
    )
}
