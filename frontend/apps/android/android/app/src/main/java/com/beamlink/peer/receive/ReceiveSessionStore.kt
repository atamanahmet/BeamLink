package com.beamlink.peer.receive

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds live inbound transfers.
 */
class ReceiveSessionStore {

    private val sessions = ConcurrentHashMap<UUID, ReceiveSession>()

    fun put(session: ReceiveSession) {
        sessions[session.transferId] = session
    }

    fun get(id: UUID): ReceiveSession? = sessions[id]

    fun require(id: UUID): ReceiveSession {
        return sessions[id] ?: throw ProtocolException(
            "SESSION_NOT_FOUND",
            "Receiver lost the transfer, send again",
            404,
        )
    }

    fun remove(id: UUID) {
        sessions.remove(id)
    }

    fun all(): Collection<ReceiveSession> = sessions.values
}
