package com.beamlink.peer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.beamlink.peer.event.ProgressBus
import com.beamlink.peer.net.AddressGuard
import com.beamlink.peer.net.NexusClient
import com.beamlink.peer.security.ProbeFailure
import com.beamlink.peer.receive.DiskReserve
import com.beamlink.peer.receive.ProtocolException
import com.beamlink.peer.receive.ReceiveForegroundService
import com.beamlink.peer.receive.ReceiveSession
import com.beamlink.peer.send.TransferForegroundService
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginHandle
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import java.util.concurrent.Executors

/**
 * Bridges WebView calls to registration, peers, send, and receive. No networking here.
 */
@CapacitorPlugin(
    name = "Beamlink",
    permissions = [
        Permission(
            strings = [Manifest.permission.POST_NOTIFICATIONS],
            alias = "notifications",
        ),
    ],
)
class BeamlinkPlugin : Plugin() {

    private val nexusClient = NexusClient()
    private val cancelExecutor = Executors.newSingleThreadExecutor()
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private var pickCall: PluginCall? = null
    private var sendProgressCb: ((Long, Long, String, String?) -> Unit)? = null
    private var progressBusCb: ((
        Long, Long, String, String?, String, String?, String?,
    ) -> Unit)? = null
    private var toggleCb: ((Boolean, String) -> Unit)? = null

    override fun load() {
        AppGraph.init(context)
        AppGraph.onRegistrationState = { state, message ->
            val payload = JSObject()
            payload.put("state", state)
            payload.put("message", message)
            payload.put("hasTokens", AppGraph.tokenStore.load() != null)
            payload.put("fingerprint", AppGraph.registration.fingerprint())
            notifyListeners("registrationState", payload, true)
        }
        AppGraph.onAuthLost = { AppGraph.registration.recoverAfterAuthLoss() }
        val sendCb: (Long, Long, String, String?) -> Unit =
            { bytes, total, state, error ->
                emitProgress(bytes, total, state, error, "send")
            }
        sendProgressCb = sendCb
        TransferForegroundService.progressListener = sendCb
        val busCb: (Long, Long, String, String?, String, String?, String?) -> Unit =
            { bytes, total, state, error, direction, name, peer ->
                emitProgress(bytes, total, state, error, direction, name, peer)
            }
        progressBusCb = busCb
        ProgressBus.listener = busCb
        val toggle: (Boolean, String) -> Unit = { enabled, message ->
            emitReceiveToggle(enabled, message)
        }
        toggleCb = toggle
        ReceiveForegroundService.toggleListener = toggle
        if (AppGraph.prefs.receiveEnabled) {
            ReceiveForegroundService.start(context)
        }
    }

    /**
     * Drops this plugin's callbacks only. Leaves a newer plugin's listeners.
     */
    override fun handleOnDestroy() {
        val sendMine = sendProgressCb
        if (sendMine != null && TransferForegroundService.progressListener === sendMine) {
            TransferForegroundService.progressListener = null
        }
        val toggleMine = toggleCb
        if (toggleMine != null && ReceiveForegroundService.toggleListener === toggleMine) {
            ReceiveForegroundService.toggleListener = null
        }
        val busMine = progressBusCb
        if (busMine != null && ProgressBus.listener === busMine) {
            ProgressBus.listener = null
        }
        super.handleOnDestroy()
    }

    /**
     * Pauses enrollment poll while the app is in the background.
     */
    override fun handleOnPause() {
        if (AppGraph.ready) {
            AppGraph.registration.onAppBackground()
        }
        super.handleOnPause()
    }

    /**
     * Pushes the live receive switch and resumes the enrollment poll.
     */
    override fun handleOnResume() {
        emitReceiveToggle(ReceiveForegroundService.running, "")
        if (AppGraph.ready) {
            AppGraph.registration.onAppForeground()
        }
        super.handleOnResume()
    }

    /**
     * Tells the web view the receive switch state.
     */
    private fun emitReceiveToggle(enabled: Boolean, message: String) {
        val payload = JSObject()
        payload.put("enabled", enabled)
        payload.put("message", message)
        notifyListeners("receiveToggle", payload, true)
    }

    /**
     * Pushes a send or receive progress event to the WebView.
     */
    private fun emitProgress(
        bytes: Long,
        total: Long,
        state: String,
        error: String?,
        direction: String,
        name: String? = null,
        peer: String? = null,
    ) {
        val payload = JSObject()
        payload.put("bytes", bytes)
        payload.put("total", total)
        payload.put("state", state)
        payload.put("direction", direction)
        if (error != null) {
            payload.put("error", error)
        }
        if (name != null) {
            payload.put("name", name)
        }
        if (peer != null) {
            payload.put("peer", peer)
        }
        notifyListeners("sendProgress", payload, true)
    }

    /**
     * Returns the stored nexus address.
     */
    @PluginMethod
    fun getNexusAddress(call: PluginCall) {
        val result = JSObject()
        result.put("url", AppGraph.registration.getNexusAddress())
        call.resolve(result)
    }

    /**
     * Validates and stores the nexus address.
     */
    @PluginMethod
    fun setNexusAddress(call: PluginCall) {
        val url = call.getString("url") ?: ""
        try {
            AppGraph.registration.setNexusAddress(url)
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "Invalid nexus address")
        }
    }

    /**
     * Handshake-only TLS probe. Does not store the pin.
     */
    @PluginMethod
    fun probeNexus(call: PluginCall) {
        val url = call.getString("url") ?: ""
        probeExecutor.execute {
            try {
                val fingerprint = AppGraph.registration.probe(url)
                val result = JSObject()
                result.put("fingerprint", fingerprint)
                result.put("url", AppGraph.registration.getNexusAddress())
                call.resolve(result)
            } catch (e: Exception) {
                call.reject(e.message ?: ProbeFailure.UNREACHABLE)
            }
        }
    }

    /**
     * Stores the pin after the person confirms the probed fingerprint.
     */
    @PluginMethod
    fun confirmNexusPin(call: PluginCall) {
        val url = call.getString("url") ?: ""
        val fingerprint = call.getString("fingerprint") ?: ""
        try {
            AppGraph.registration.confirm(url, fingerprint)
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "Nexus TLS confirm failed")
        }
    }

    /**
     * Clears the pin. Re-pin is an explicit user action.
     */
    @PluginMethod
    fun clearNexusPin(call: PluginCall) {
        AppGraph.registration.clearPin()
        call.resolve()
    }

    /**
     * Pin and last probe snapshot.
     */
    @PluginMethod
    fun getNexusTlsStatus(call: PluginCall) {
        val result = JSObject()
        result.put("url", AppGraph.registration.getNexusAddress())
        result.put("pinSet", AppGraph.registration.hasPin())
        result.put("pendingFingerprint", AppGraph.registration.pendingFingerprint())
        call.resolve(result)
    }

    /**
     * Returns registration state for the UI.
     */
    @PluginMethod
    fun getRegistrationStatus(call: PluginCall) {
        val (state, message) = AppGraph.registration.status()
        val result = JSObject()
        result.put("state", state)
        result.put("message", message)
        result.put("hasTokens", AppGraph.registration.hasTokens())
        result.put("deviceId", AppGraph.prefs.deviceId)
        result.put("fingerprint", AppGraph.registration.fingerprint())
        call.resolve(result)
    }

    /**
     * Registers with nexus using the desktop contract.
     */
    @PluginMethod
    fun register(call: PluginCall) {
        AppGraph.registration.register()
        call.resolve()
    }

    /**
     * Clears tokens and registers again.
     */
    @PluginMethod
    fun reRegister(call: PluginCall) {
        AppGraph.registration.reRegister()
        call.resolve()
    }

    /**
     * Local reset. Nexus row stays until an admin clicks Remove.
     */
    @PluginMethod
    fun resetDevice(call: PluginCall) {
        AppGraph.deviceReset.run(context.applicationContext)
        emitReceiveToggle(false, "")
        call.resolve()
    }

    /**
     * Starts identify polling when pending.
     */
    @PluginMethod
    fun startIdentifyPoll(call: PluginCall) {
        AppGraph.registration.startIdentifyPoll()
        call.resolve()
    }

    /**
     * Stops identify polling.
     */
    @PluginMethod
    fun stopIdentifyPoll(call: PluginCall) {
        AppGraph.registration.stopIdentifyPoll()
        call.resolve()
    }

    /**
     * Fetches peer summaries and filters out self by deviceId.
     */
    @PluginMethod
    fun getPeers(call: PluginCall) {
        val tokens = AppGraph.tokenStore.load()
        if (tokens == null) {
            call.reject("Waiting for approval. Tokens not available.")
            return
        }
        val nexusUrl = AppGraph.prefs.nexusUrl
        if (nexusUrl.isBlank()) {
            call.reject("Nexus address is not configured")
            return
        }
        try {
            if (AppGraph.prefs.tlsPin.isBlank()) {
                call.reject(com.beamlink.peer.security.NexusTlsPin.MISSING_PIN)
                return
            }
            AddressGuard.requireAllowedNexusUrl(nexusUrl)
            val peers = nexusClient.fetchPeerSummaries(nexusUrl, tokens.publicToken)
            val selfId = AppGraph.prefs.deviceId.ifBlank { tokens.deviceId }
            val array = JSArray()
            for (peer in peers) {
                if (peer.id == selfId) continue
                val item = JSObject()
                item.put("id", peer.id)
                item.put("deviceName", peer.deviceName)
                item.put("ipAddress", peer.ipAddress)
                item.put("port", peer.port)
                item.put("online", peer.online)
                if (peer.tlsPort != null) {
                    item.put("tlsPort", peer.tlsPort)
                }
                if (peer.tlsFingerprint != null) {
                    item.put("tlsFingerprint", peer.tlsFingerprint)
                }
                array.put(item)
            }
            val result = JSObject()
            result.put("peers", array)
            call.resolve(result)
        } catch (e: Exception) {
            call.reject(e.message ?: "Peer list failed")
        }
    }

    /**
     * Opens the system multi-document picker. Same SharedFileItem shape as share.
     */
    @PluginMethod
    fun pickFiles(call: PluginCall) {
        pickCall?.reject("Cancelled")
        call.setKeepAlive(true)
        pickCall = call
        val host = activity as? MainActivity
        if (host == null) {
            pickCall = null
            call.reject("Picker unavailable")
            return
        }
        host.launchDocumentPicker()
    }

    /**
     * Resolves pickFiles with content URI metadata. Does not copy bytes.
     */
    fun onDocumentsPicked(uris: List<Uri>) {
        val call = pickCall ?: return
        pickCall = null
        val resolver = context.contentResolver
        val files = JSArray()
        for (uri in uris) {
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Session grant from the picker is enough for this send.
            }
            files.put(ContentUriMeta.read(resolver, uri))
        }
        val result = JSObject()
        result.put("files", files)
        call.resolve(result)
    }

    /**
     * Starts foreground send for shared file refs (uri, name, size).
     */
    @PluginMethod
    fun sendFiles(call: PluginCall) {
        if (AppGraph.tokenStore.load() == null) {
            call.reject("Waiting for approval. Tokens not available.")
            return
        }
        val files = call.getArray("files")
        if (files == null || files.length() == 0) {
            call.reject("No files to send")
            return
        }
        val targetIp = call.getString("targetIp") ?: ""
        val targetPort = call.getInt("targetPort") ?: 0
        val targetDeviceId = call.getString("targetDeviceId") ?: ""
        val tlsFingerprint = call.getString("tlsFingerprint")?.takeIf { it.isNotBlank() }
        val tlsPortRaw = call.data.optInt("tlsPort", -1)
        val tlsPort = tlsPortRaw.takeIf { it > 0 }
        try {
            AddressGuard.requireAllowedHost(targetIp)
        } catch (e: Exception) {
            call.reject(e.message ?: "Peer address refused")
            return
        }
        val uris = ArrayList<String>()
        val names = ArrayList<String>()
        val sizes = LongArray(files.length())
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i)
            uris.add(item.getString("uri"))
            names.add(item.optString("name", "file"))
            sizes[i] = item.optLong("size", 0L)
        }
        TransferForegroundService.startSend(
            context,
            uris,
            names,
            sizes,
            targetIp,
            targetPort,
            targetDeviceId,
            tlsFingerprint,
            tlsPort,
        )
        call.resolve()
    }

    /**
     * Cancels the active foreground send.
     */
    @PluginMethod
    fun cancelSend(call: PluginCall) {
        TransferForegroundService.cancel(context)
        call.resolve()
    }

    /**
     * Restarts a paused send from the receiver offset. User must be in the app.
     */
    @PluginMethod
    fun resumeSend(call: PluginCall) {
        if (AppGraph.tokenStore.load() == null) {
            call.reject("Waiting for approval. Tokens not available.")
            return
        }
        if (!TransferForegroundService.hasPausedJob()) {
            call.reject("Nothing to resume")
            return
        }
        TransferForegroundService.resume(context)
        call.resolve()
    }

    /**
     * Returns whether the receive foreground service is running.
     */
    @PluginMethod
    fun getReceiveEnabled(call: PluginCall) {
        val result = JSObject()
        result.put("enabled", ReceiveForegroundService.running)
        call.resolve(result)
    }

    /**
     * Last send and receive slot snapshots owned by native.
     */
    @PluginMethod
    fun getTransferSlots(call: PluginCall) {
        val slots = JSArray()
        for (slot in ProgressBus.snapshot()) {
            val item = JSObject()
            item.put("bytes", slot.bytes)
            item.put("total", slot.total)
            item.put("state", slot.state)
            item.put("direction", slot.direction)
            if (slot.error != null) {
                item.put("error", slot.error)
            }
            if (slot.name != null) {
                item.put("name", slot.name)
            }
            if (slot.peer != null) {
                item.put("peer", slot.peer)
            }
            slots.put(item)
        }
        val result = JSObject()
        result.put("slots", slots)
        call.resolve(result)
    }

    /**
     * Turns the receive foreground service on or off. Default is off.
     */
    @PluginMethod
    fun setReceiveEnabled(call: PluginCall) {
        val enabled = call.getBoolean("enabled") ?: false
        if (enabled) {
            maybeRequestNotifications()
            AppGraph.prefs.receiveEnabled = true
            ReceiveForegroundService.start(context)
        } else {
            AppGraph.prefs.receiveEnabled = false
            ReceiveForegroundService.stop(context)
        }
        val result = JSObject()
        result.put("enabled", enabled)
        call.resolve(result)
    }

    /**
     * Dark status bar icons when light is true. Light icons when false.
     */
    @PluginMethod
    fun setStatusBarLight(call: PluginCall) {
        val light = call.getBoolean("light") ?: false
        val host = activity
        if (host == null) {
            call.reject("Activity unavailable")
            return
        }
        host.runOnUiThread {
            val window = host.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = light
            call.resolve()
        }
    }

    /**
     * Cancels inbound transfers and notifies each sender.
     */
    /**
     * Returns the stored disk reserve in bytes.
     */
    @PluginMethod
    fun getMinFreeSpaceBytes(call: PluginCall) {
        val result = JSObject()
        result.put("value", AppGraph.prefs.minFreeSpaceBytes)
        call.resolve(result)
    }

    /**
     * Saves the disk reserve in bytes and refreshes the in-memory provider.
     */
    @PluginMethod
    fun setMinFreeSpaceBytes(call: PluginCall) {
        val value = call.getLong("value")
        if (value == null) {
            call.reject("value is required")
            return
        }
        try {
            AppGraph.prefs.minFreeSpaceBytes = value
            AppGraph.diskReserveProvider.refresh()
        } catch (e: IllegalArgumentException) {
            call.reject(e.message ?: "Invalid setting value")
            return
        }
        val result = JSObject()
        result.put("value", AppGraph.prefs.minFreeSpaceBytes)
        call.resolve(result)
    }

    @PluginMethod
    fun cancelReceive(call: PluginCall) {
        cancelExecutor.execute {
            AppGraph.receiveCancel.cancelFromPhone()
        }
        call.resolve()
    }

    /**
     * Resumes a paused receive. Stays paused when disk is still short.
     */
    @PluginMethod
    fun resumeReceive(call: PluginCall) {
        cancelExecutor.execute {
            try {
                val session = AppGraph.sessions.all().firstOrNull {
                    it.status == ReceiveSession.PAUSED
                }
                if (session != null) {
                    AppGraph.chunkReceiver.resume(session.transferId)
                }
                call.resolve()
            } catch (e: ProtocolException) {
                if (e.code == DiskReserve.CODE) {
                    call.resolve()
                } else {
                    call.reject(e.message ?: "Resume failed")
                }
            } catch (e: Exception) {
                call.reject(e.message ?: "Resume failed")
            }
        }
    }

    /**
     * Asks the web view to open the transfers screen.
     */
    fun openTransfers() {
        notifyListeners("openTransfers", JSObject(), true)
    }

    /**
     * Asks for POST_NOTIFICATIONS on API 33+.
     */
    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            val host = activity ?: return
            ActivityCompat.requestPermissions(
                host,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                47,
            )
        }
    }

    companion object {
        /**
         * Finds the registered Beamlink plugin on the bridge.
         */
        fun from(handle: PluginHandle?): BeamlinkPlugin? {
            return handle?.instance as? BeamlinkPlugin
        }
    }
}
