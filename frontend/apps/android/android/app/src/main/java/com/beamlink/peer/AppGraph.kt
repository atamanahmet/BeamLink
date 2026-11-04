package com.beamlink.peer

import android.content.Context
import com.beamlink.peer.event.ProgressBus
import com.beamlink.peer.receive.ChunkReceiver
import com.beamlink.peer.receive.DiskReserve
import com.beamlink.peer.receive.DiskReserveProvider
import com.beamlink.peer.receive.MediaStorePublisher
import com.beamlink.peer.receive.ReceiveCancel
import com.beamlink.peer.receive.ReceiveServer
import com.beamlink.peer.receive.ReceiveSession
import com.beamlink.peer.receive.ReceiveSessionStore
import com.beamlink.peer.receive.ReceiveStallTracker
import com.beamlink.peer.receive.ReceiveStallWatchdog
import com.beamlink.peer.register.DeviceReset
import com.beamlink.peer.register.EnrollmentPollOwnerRule
import com.beamlink.peer.register.NexusPrefs
import com.beamlink.peer.register.RegistrationCoordinator
import com.beamlink.peer.security.DeviceKey
import com.beamlink.peer.security.PhoneTlsCert
import com.beamlink.peer.security.TokenStore

/**
 * App-wide receive and register pieces. Init once from the plugin.
 */
object AppGraph {

    lateinit var prefs: NexusPrefs
        private set
    lateinit var tokenStore: TokenStore
        private set
    lateinit var deviceKey: DeviceKey
        private set
    lateinit var phoneTlsCert: PhoneTlsCert
        private set
    val sessions = ReceiveSessionStore()
    lateinit var chunkReceiver: ChunkReceiver
        private set
    lateinit var receiveStallWatchdog: ReceiveStallWatchdog
        private set
    lateinit var diskReserveProvider: DiskReserveProvider
        private set
    lateinit var receiveServer: ReceiveServer
        private set
    lateinit var receiveCancel: ReceiveCancel
        private set
    lateinit var registration: RegistrationCoordinator
        private set
    lateinit var deviceReset: DeviceReset
        private set

    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    var onAuthLost: (() -> Unit)? = null

    @Volatile
    var onRegistrationState: ((String, String) -> Unit)? = null

    /**
     * Builds receive services from application context.
     */
    fun init(context: Context) {
        if (ready) return
        val app = context.applicationContext
        prefs = NexusPrefs(app)
        tokenStore = TokenStore(app)
        deviceKey = DeviceKey(app)
        phoneTlsCert = PhoneTlsCert()
        phoneTlsCert.loadOrCreate()
        val publisher = MediaStorePublisher(app)
        diskReserveProvider = DiskReserveProvider(prefs)
        val stallTracker = ReceiveStallTracker()
        chunkReceiver = ChunkReceiver(
            app,
            sessions,
            publisher,
            prefs,
            diskReserveProvider,
            tokenStore,
            stallTracker,
        ) { session ->
            emitSession(session)
        }
        receiveStallWatchdog = ReceiveStallWatchdog(sessions, stallTracker) { id ->
            chunkReceiver.pause(id, "TARGET")
        }
        receiveServer = ReceiveServer(chunkReceiver, tokenStore, phoneTlsCert)
        receiveCancel = ReceiveCancel(sessions, chunkReceiver, tokenStore)
        registration = RegistrationCoordinator(
            prefs,
            tokenStore,
            deviceKey,
            phoneTlsCert,
        ) { state, message ->
            onRegistrationState?.invoke(state, message)
        }
        deviceReset = DeviceReset(
            tokenStore,
            prefs,
            deviceKey,
            phoneTlsCert,
            registration,
            receiveServer,
            chunkReceiver,
        )
        ready = true
        registration.applyPollOwner(
            EnrollmentPollOwnerRule.onPersistedState(prefs.registrationState),
        )
    }

    /**
     * Maps a receive session to the shared progress event.
     */
    fun emitSession(session: ReceiveSession) {
        val state = when (session.status) {
            ReceiveSession.ACTIVE -> "receiving"
            ReceiveSession.PAUSED -> "paused"
            ReceiveSession.COMPLETED -> "completed"
            ReceiveSession.CANCELLED -> "cancelled"
            ReceiveSession.FAILED -> "error"
            else -> session.status.lowercase()
        }
        ProgressBus.emit(
            session.confirmedOffset,
            session.fileSize,
            state,
            readableFailure(session.failureReason),
            "receive",
            session.fileName,
            session.sourceDeviceId,
        )
    }

    /**
     * Maps INSUFFICIENT_DISK to the shared mapper text.
     */
    private fun readableFailure(reason: String?): String? {
        if (reason == DiskReserve.CODE) {
            return DiskReserve.DETAIL
        }
        return reason
    }
}
