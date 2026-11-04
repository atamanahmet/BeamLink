package com.beamlink.peer.receive

import com.beamlink.peer.AppGraph
import com.beamlink.peer.net.NexusClient
import com.beamlink.peer.net.PeerTransferClient
import com.beamlink.peer.security.TokenStore

/**
 * Phone-started cancel: local abort then cancel-notify to the sender.
 */
class ReceiveCancel(
    private val store: ReceiveSessionStore,
    private val receiver: ChunkReceiver,
    private val tokenStore: TokenStore,
    private val peerClient: PeerTransferClient = PeerTransferClient(),
    private val nexusClient: NexusClient = NexusClient(),
) {

    /**
     * Cancels every live receive and tells each sender.
     */
    fun cancelFromPhone() {
        val tokens = tokenStore.load()
        val snapshot = store.all().toList()
        for (session in snapshot) {
            val ip = session.sourceIp
            val port = session.sourcePort
            receiver.cancel(session.transferId)
            if (tokens == null || ip.isNullOrBlank() || port == null) {
                continue
            }
            try {
                val listed = lookupPeer(ip, port, tokens.publicToken)
                peerClient.notifyCancel(
                    ip,
                    port,
                    session.transferId,
                    tokens.publicToken,
                    tokens.publicId,
                    listed?.tlsFingerprint,
                    listed?.tlsPort,
                )
            } catch (_: Exception) {
                // Desktop HTTP client swallows peer notify failures.
            }
        }
    }

    /**
     * TLS pin for the sender comes from the nexus peer list, never from the peer.
     */
    private fun lookupPeer(ip: String, port: Int, publicToken: String): NexusClient.PeerSummary? {
        if (!AppGraph.ready) {
            return null
        }
        val url = AppGraph.prefs.nexusUrl
        if (url.isBlank()) {
            return null
        }
        return try {
            nexusClient.fetchPeerSummaries(url, publicToken).find { peer ->
                peer.ipAddress == ip && (peer.port == port || peer.tlsPort == port)
            }
        } catch (_: Exception) {
            null
        }
    }
}
