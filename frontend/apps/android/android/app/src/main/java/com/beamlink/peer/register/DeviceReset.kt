package com.beamlink.peer.register

import android.content.Context
import com.beamlink.peer.receive.ChunkReceiver
import com.beamlink.peer.receive.ReceiveForegroundService
import com.beamlink.peer.receive.ReceiveServer
import com.beamlink.peer.security.DeviceKey
import com.beamlink.peer.security.PhoneTlsCert
import com.beamlink.peer.security.TokenStore

/**
 * Local factory reset. Tokens and prefs die before keys so a crash stays unregistered.
 */
class DeviceReset(
    private val tokenStore: TokenStore,
    private val prefs: NexusPrefs,
    private val deviceKey: DeviceKey,
    private val phoneTlsCert: PhoneTlsCert,
    private val coordinator: RegistrationCoordinator,
    private val receiveServer: ReceiveServer,
    private val chunkReceiver: ChunkReceiver,
) {

    /**
     * Cancels enroll, stops receive, then wipes tokens, prefs, and keys.
     */
    fun run(app: Context) {
        coordinator.cancelEnrollAndJoin()
        coordinator.stopIdentifyPoll()
        try {
            receiveServer.stop()
        } catch (_: Exception) {
        }
        try {
            chunkReceiver.abortAll()
        } catch (_: Exception) {
        }
        val receiveWasOn = ReceiveForegroundService.running
        ReceiveForegroundService.running = false
        if (receiveWasOn) {
            ReceiveForegroundService.stop(app)
        }
        tokenStore.clear()
        coordinator.clearPin()
        prefs.clear()
        deviceKey.delete()
        phoneTlsCert.delete()
        coordinator.emitFirstRun()
    }
}
