package com.beamlink.peer.net

import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.receive.DiskPauseRule
import com.beamlink.peer.receive.DiskReserve
import com.beamlink.peer.security.PeerTransport
import com.beamlink.peer.send.ConnectTimeoutText
import com.beamlink.peer.util.ChunkOffsets
import com.beamlink.peer.util.FileChecksum
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Peer transfer HTTP: receive, offset, chunk. Same headers as DeviceAuthHttpSender.
 */
class PeerTransferClient(
    private val http: HttpJsonClient = HttpJsonClient(),
) {
    private val activeHttp = AtomicReference(http)

    @Volatile
    var activeTransferId: UUID? = null
        private set

    class ChunkHttpException(val statusCode: Int, message: String) : Exception(message)
    class ChunkChecksumException : Exception("CHUNK_CHECKSUM_MISMATCH")
    class CancelledException : Exception("Transfer cancelled")

    /**
     * Aborts the in-flight OkHttp call used by this client.
     */
    fun cancel() {
        activeHttp.get().cancelActiveCall()
        http.cancelActiveCall()
    }

    /**
     * POST /api/transfers/receive then streams chunks with retry.
     * skipRegister true means resume: keep transferId, query offset, no POST /receive.
     */
    fun sendFile(
        targetIp: String,
        targetPort: Int,
        sourceDeviceId: String,
        publicToken: String,
        publicId: String,
        fileName: String,
        fileSize: Long,
        sourceIp: String,
        sourcePort: Int,
        readChunk: (buffer: ByteArray) -> Int,
        onProgress: (bytesSent: Long) -> Unit,
        isCancelled: () -> Boolean,
        tlsFingerprint: String? = null,
        tlsPort: Int? = null,
        transferId: UUID = UUID.randomUUID(),
        skipRegister: Boolean = false,
    ): UUID {
        val authHeaders = mapOf(
            "X-Public-Token" to publicToken,
            "X-Public-Id" to publicId,
        )
        activeTransferId = transferId
        try {
            val transport = PeerTransport.of(tlsFingerprint, tlsPort, targetPort)
            val baseUrl = transport.baseUrl(targetIp)
            AddressGuard.requireAllowedPeerUrl(baseUrl)
            val http = clientFor(transport)
            if (isCancelled()) {
                cancel()
                throw CancelledException()
            }
            if (!skipRegister) {
                registerReceive(
                    http,
                    baseUrl,
                    authHeaders,
                    transferId,
                    sourceDeviceId,
                    fileName,
                    fileSize,
                    sourceIp,
                    sourcePort,
                )
            }
            var offset = queryConfirmedOffset(http, baseUrl, authHeaders, transferId)
            if (offset > 0L) {
                skipBytes(readChunk, offset)
            }
            val buffer = ByteArray(DeviceDefaults.CHUNK_SIZE_BYTES)
            while (true) {
                if (isCancelled()) {
                    cancel()
                    throw CancelledException()
                }
                val bytesRead = readChunk(buffer)
                if (bytesRead <= 0) break
                val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                try {
                    sendChunkWithRetry(
                        http,
                        baseUrl,
                        authHeaders,
                        transferId,
                        offset,
                        fileSize,
                        chunk,
                        isCancelled,
                    )
                } catch (e: Exception) {
                    if (isCancelled()) throw CancelledException()
                    throw e
                }
                offset = ChunkOffsets.nextOffset(offset, bytesRead)
                onProgress(offset)
            }
            return transferId
        } finally {
            if (activeTransferId == transferId) {
                activeTransferId = null
            }
        }
    }

    /**
     * Tells the peer to cancel their side.
     */
    fun notifyCancel(
        peerIp: String,
        peerPort: Int,
        transferId: UUID,
        publicToken: String,
        publicId: String,
        tlsFingerprint: String? = null,
        tlsPort: Int? = null,
    ) {
        val headers = mapOf(
            "X-Public-Token" to publicToken,
            "X-Public-Id" to publicId,
        )
        val transport = PeerTransport.of(tlsFingerprint, tlsPort, peerPort)
        val baseUrl = transport.baseUrl(peerIp)
        AddressGuard.requireAllowedPeerUrl(baseUrl)
        clientFor(transport).delete(
            "$baseUrl/api/transfers/$transferId/cancel-notify",
            headers,
            timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
        )
    }

    /**
     * Tells the peer to pause their side.
     */
    fun notifyPause(
        peerIp: String,
        peerPort: Int,
        transferId: UUID,
        publicToken: String,
        publicId: String,
        pausedBy: String,
        reason: String?,
        tlsFingerprint: String? = null,
        tlsPort: Int? = null,
    ) {
        val headers = mapOf(
            "X-Public-Token" to publicToken,
            "X-Public-Id" to publicId,
        )
        val transport = PeerTransport.of(tlsFingerprint, tlsPort, peerPort)
        val baseUrl = transport.baseUrl(peerIp)
        AddressGuard.requireAllowedPeerUrl(baseUrl)
        val body = JSONObject().put("pausedBy", pausedBy)
        if (!reason.isNullOrBlank()) {
            body.put("reason", reason)
        }
        clientFor(transport).postJson(
            "$baseUrl/api/transfers/$transferId/pause-notify",
            body,
            headers,
            timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
        )
    }

    /**
     * Tells the peer to resume their side.
     */
    fun notifyResume(
        peerIp: String,
        peerPort: Int,
        transferId: UUID,
        publicToken: String,
        publicId: String,
        tlsFingerprint: String? = null,
        tlsPort: Int? = null,
    ) {
        val headers = mapOf(
            "X-Public-Token" to publicToken,
            "X-Public-Id" to publicId,
        )
        val transport = PeerTransport.of(tlsFingerprint, tlsPort, peerPort)
        val baseUrl = transport.baseUrl(peerIp)
        AddressGuard.requireAllowedPeerUrl(baseUrl)
        val response = clientFor(transport).postJson(
            "$baseUrl/api/transfers/$transferId/resume-notify",
            JSONObject(),
            headers,
            timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
        )
        if (DiskPauseRule.senderNoRetry(response.status)) {
            throw ChunkHttpException(response.status, DiskReserve.DETAIL)
        }
    }

    private fun clientFor(transport: PeerTransport): HttpJsonClient {
        val next = HttpJsonClient(HttpJsonClient.pinnedClient(transport.pin))
        activeHttp.set(next)
        return next
    }

    private fun registerReceive(
        http: HttpJsonClient,
        baseUrl: String,
        authHeaders: Map<String, String>,
        transferId: UUID,
        sourceDeviceId: String,
        fileName: String,
        fileSize: Long,
        sourceIp: String,
        sourcePort: Int,
    ) {
        val body = JSONObject()
            .put("transferId", transferId.toString())
            .put("sourceDeviceId", sourceDeviceId)
            .put("fileName", fileName)
            .put("sourceIp", sourceIp)
            .put("sourcePort", sourcePort)
            .put("fileSize", fileSize)
        val response = try {
            http.postJson(
                "$baseUrl/api/transfers/receive",
                body,
                headers = authHeaders,
                timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
            )
        } catch (e: IOException) {
            val host = java.net.URI(baseUrl).host
            val text = ConnectTimeoutText.of(e, host)
                ?: e.message?.takeIf { it.isNotBlank() }
                ?: "Cannot reach target. It may be offline or have receive turned off."
            throw IllegalStateException(text, e)
        }
        if (DiskPauseRule.senderNoRetry(response.status)) {
            throw ChunkHttpException(response.status, DiskReserve.DETAIL)
        }
        if (response.status !in 200..299) {
            throw IllegalStateException(
                ProblemBody.userText(response.body, response.status),
            )
        }
    }

    /**
     * Retries like desktop sendChunkWithRetry for RETRY, OFFLINE, CHUNK_CORRUPT.
     */
    private fun sendChunkWithRetry(
        http: HttpJsonClient,
        baseUrl: String,
        authHeaders: Map<String, String>,
        transferId: UUID,
        offset: Long,
        fileSize: Long,
        chunk: ByteArray,
        isCancelled: () -> Boolean,
    ) {
        var last: Exception? = null
        for (attempt in 1..DeviceDefaults.MAX_RETRIES) {
            if (isCancelled()) {
                cancel()
                throw CancelledException()
            }
            try {
                if (attempt > 1) {
                    val confirmed = queryConfirmedOffset(http, baseUrl, authHeaders, transferId)
                    if (confirmed > offset) {
                        return
                    }
                }
                sendChunk(http, baseUrl, authHeaders, transferId, offset, fileSize, chunk)
                return
            } catch (e: ChunkChecksumException) {
                last = e
            } catch (e: ChunkHttpException) {
                if (!allowsRetry(e.statusCode)) throw e
                last = e
            } catch (e: Exception) {
                if (isCancelled()) throw CancelledException()
                last = e
            }
            if (attempt < DeviceDefaults.MAX_RETRIES) {
                Thread.sleep(DeviceDefaults.RETRY_DELAY_MS * attempt)
            }
        }
        throw IllegalStateException(
            "Chunk failed after ${DeviceDefaults.MAX_RETRIES} attempts at offset $offset",
            last,
        )
    }

    private fun sendChunk(
        http: HttpJsonClient,
        baseUrl: String,
        authHeaders: Map<String, String>,
        transferId: UUID,
        offset: Long,
        fileSize: Long,
        chunk: ByteArray,
    ) {
        val headers = LinkedHashMap(authHeaders)
        headers["Content-Range"] = ChunkOffsets.contentRange(offset, chunk.size, fileSize)
        headers[DeviceDefaults.CHUNK_SHA256_HEADER] = FileChecksum.sha256Hex(chunk)
        val response = http.patchBytes(
            "$baseUrl/api/transfers/$transferId/chunk",
            chunk,
            headers,
            timeoutMs = DeviceDefaults.READ_TIMEOUT_MS.toInt(),
        )
        if (response.status == 422) {
            throw ChunkChecksumException()
        }
        if (response.status != 200) {
            throw ChunkHttpException(
                response.status,
                ProblemBody.userText(response.body, response.status),
            )
        }
    }

    /**
     * GET /api/transfers/{id}/offset confirmedOffset.
     */
    private fun queryConfirmedOffset(
        http: HttpJsonClient,
        baseUrl: String,
        authHeaders: Map<String, String>,
        transferId: UUID,
    ): Long {
        val response = http.get(
            "$baseUrl/api/transfers/$transferId/offset",
            headers = authHeaders,
            timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
        )
        if (response.status !in 200..299) {
            throw IllegalStateException(
                ProblemBody.userText(response.body, response.status),
            )
        }
        return try {
            HttpJsonClient.parseObject(response.body).optLong("confirmedOffset", 0L)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Drops already confirmed bytes from the sequential stream.
     */
    private fun skipBytes(readChunk: (ByteArray) -> Int, toSkip: Long) {
        var left = toSkip
        while (left > 0L) {
            val want = minOf(left, DeviceDefaults.CHUNK_SIZE_BYTES.toLong()).toInt()
            val buf = ByteArray(want)
            val n = readChunk(buf)
            if (n <= 0) break
            left -= n.toLong()
        }
    }

    private fun allowsRetry(status: Int): Boolean {
        if (DiskPauseRule.senderNoRetry(status)) return false
        if (status == 422) return true
        if (status == 408 || status == 429) return true
        if (status in 500..599) return true
        return false
    }
}
