package com.beamlink.peer.receive

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.beamlink.peer.AppGraph
import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.net.NexusClient
import com.beamlink.peer.net.PeerTransferClient
import com.beamlink.peer.register.NexusPrefs
import com.beamlink.peer.security.TokenStore
import com.beamlink.peer.util.FileNameSanitizer
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Prepares pending Downloads rows, writes hashed chunks, clears IS_PENDING on complete.
 */
class ChunkReceiver(
    private val context: Context,
    private val store: ReceiveSessionStore,
    private val publisher: MediaStorePublisher,
    private val prefs: NexusPrefs,
    private val reserveProvider: DiskReserveProvider,
    private val tokenStore: TokenStore,
    private val stallTracker: ReceiveStallTracker,
    private val peerClient: PeerTransferClient = PeerTransferClient(),
    private val nexusClient: NexusClient = NexusClient(),
    private val onProgress: (ReceiveSession) -> Unit,
) {

    init {
        restorePersisted()
    }

    /**
     * Disk check then create a pending Downloads row. 507 before writing.
     */
    fun prepare(
        transferId: UUID,
        fileName: String,
        fileSize: Long,
        sourceDeviceId: String?,
        sourceIp: String?,
        sourcePort: Int?,
        batchTransferId: UUID?,
        skipDiskCheck: Boolean = false,
    ) {
        FileSizeRule.requireValid(fileSize)
        if (!skipDiskCheck) {
            rejectIfBusy(setOf(transferId))
        }
        if (!skipDiskCheck && !hasRoom(fileSize)) {
            throw ProtocolException(DiskReserve.CODE, DiskReserve.DETAIL, 507)
        }
        val safe = FileNameSanitizer.unique(fileName) { candidate ->
            store.all().any { it.fileName == candidate } ||
                publisher.exists(candidate)
        }
        val uri = publisher.createPending(safe)
        val session = ReceiveSession(
            transferId = transferId,
            fileName = safe,
            fileSize = fileSize,
            sourceDeviceId = sourceDeviceId,
            sourceIp = sourceIp,
            sourcePort = sourcePort,
            batchTransferId = batchTransferId,
            tempFile = File(context.filesDir, "$transferId.part"),
            mediaUri = uri,
        )
        store.put(session)
        persist(session)
        stallTracker.touch(transferId)
        if (SendCompleteRule.shouldMarkComplete(session.status, session.confirmedOffset, session.fileSize)) {
            publisher.complete(uri)
            session.status = ReceiveSession.COMPLETED
            store.remove(transferId)
            prefs.clearReceive(transferId.toString())
            stallTracker.remove(transferId)
        }
        onProgress(session)
    }

    /**
     * One disk check for the batch total, then prepare each file.
     */
    fun prepareBatch(totalSize: Long, files: List<BatchFile>) {
        var required = 0L
        for (file in files) {
            FileSizeRule.requireValid(file.fileSize)
            required += file.fileSize
        }
        if (totalSize > 0L) {
            required = totalSize
        }
        if (!hasRoom(required)) {
            throw ProtocolException(DiskReserve.CODE, DiskReserve.DETAIL, 507)
        }
        rejectIfBusy(files.map { it.transferId }.toSet())
        for (file in files) {
            prepare(
                transferId = file.transferId,
                fileName = file.fileName,
                fileSize = file.fileSize,
                sourceDeviceId = file.sourceDeviceId,
                sourceIp = file.sourceIp,
                sourcePort = file.sourcePort,
                batchTransferId = file.batchTransferId,
                skipDiskCheck = true,
            )
        }
    }

    /**
     * Hashes chunk bytes then writes at offset.
     */
    fun receiveChunk(
        transferId: UUID,
        offset: Long,
        length: Long,
        chunkBytes: ByteArray,
        chunkSha256Header: String?,
    ): ChunkAck {
        val session = store.require(transferId)
        if (session.status == ReceiveSession.PAUSED) {
            throw ProtocolException(
                "TRANSFER_PAUSED",
                "Transfer is paused",
                409,
                pausedBy = session.pausedBy,
            )
        }
        if (session.status != ReceiveSession.ACTIVE) {
            throw ProtocolException(
                "NOT_ACTIVE",
                "Transfer is not active: ${session.status}",
                500,
            )
        }
        if (length < 0L || length > DeviceDefaults.CHUNK_SIZE_BYTES) {
            throw ProtocolException("INVALID_CHUNK_LENGTH", "Chunk length out of bounds", 400)
        }
        when (ChunkChecksumRule.check(chunkSha256Header, chunkBytes)) {
            ChunkChecksumRule.Result.MISSING -> throw ProtocolException(
                "MISSING_CHUNK_SHA256",
                "X-Chunk-SHA256 header required",
                400,
            )
            ChunkChecksumRule.Result.MISMATCH -> throw ProtocolException(
                "CHUNK_CHECKSUM_MISMATCH",
                "Chunk checksum mismatch",
                422,
            )
            ChunkChecksumRule.Result.OK -> Unit
        }
        val expected = session.confirmedOffset
        when (ReceiveOffsetRule.decide(offset, length, expected)) {
            ReceiveOffsetRule.Decision.DUPLICATE ->
                return ChunkAck(expected, expected >= session.fileSize)
            ReceiveOffsetRule.Decision.ACCEPT -> Unit
            else -> throw ProtocolException(
                "OFFSET_MISMATCH",
                "Unexpected offset. Expected: $expected",
                409,
                expectedOffset = expected,
            )
        }
        val uri = session.mediaUri
            ?: throw ProtocolException("SESSION_NOT_FOUND", "Receiver lost the transfer, send again", 404)
        if (!hasRoom(length)) {
            pauseForDisk(session)
            throw ProtocolException(DiskReserve.CODE, DiskReserve.DETAIL, 507)
        }
        try {
            publisher.writeAt(uri, offset, chunkBytes)
        } catch (e: IOException) {
            if (!hasRoom(length)) {
                pauseForDisk(session)
                throw ProtocolException(DiskReserve.CODE, DiskReserve.DETAIL, 507)
            }
            throw e
        }
        val newOffset = offset + chunkBytes.size
        session.confirmedOffset = newOffset
        persist(session)
        val complete = newOffset >= session.fileSize
        if (complete) {
            if (newOffset != session.fileSize) {
                abort(session, ReceiveSession.FAILED, "SIZE_MISMATCH")
                throw ProtocolException(
                    "SIZE_MISMATCH",
                    "Confirmed size does not match expected size",
                    409,
                )
            }
            publisher.complete(uri)
            session.status = ReceiveSession.COMPLETED
            store.remove(transferId)
            prefs.clearReceive(transferId.toString())
            stallTracker.remove(transferId)
        } else {
            stallTracker.touch(transferId)
        }
        onProgress(session)
        return ChunkAck(newOffset, complete)
    }

    /**
     * Returns the confirmed write offset for resume.
     */
    fun confirmedOffset(transferId: UUID): Long {
        return store.require(transferId).confirmedOffset
    }

    /**
     * Marks cancelled and deletes the pending entry.
     */
    fun cancel(transferId: UUID) {
        val session = store.get(transferId) ?: return
        if (session.status == ReceiveSession.ACTIVE || session.status == ReceiveSession.PAUSED) {
            abort(session, ReceiveSession.CANCELLED, null)
            onProgress(session)
        }
    }

    /**
     * Marks failed and deletes the pending entry.
     */
    fun fail(transferId: UUID, reason: String?) {
        val session = store.get(transferId) ?: return
        if (session.status == ReceiveSession.ACTIVE
            || session.status == ReceiveSession.PAUSED
            || session.status == ReceiveSession.PENDING
        ) {
            abort(session, ReceiveSession.FAILED, reason)
            onProgress(session)
        }
    }

    /**
     * Holds receive at current offset. False when this id is not a receive.
     */
    fun pause(transferId: UUID, pausedBy: String, reason: String? = null): Boolean {
        val session = store.get(transferId) ?: return false
        session.status = ReceiveSession.PAUSED
        session.pausedBy = pausedBy
        if (!reason.isNullOrBlank()) {
            session.failureReason = reason
        }
        persist(session)
        stallTracker.remove(transferId)
        onProgress(session)
        if (DiskPauseRule.isReceiverDiskPause(
                session.status,
                session.pausedBy,
                session.failureReason,
            )
        ) {
            TransferDiskNotifier.show(context, session.transferId)
        }
        return true
    }

    /**
     * Clears pause so chunks can land again. Re-checks the reserve first.
     */
    fun resume(transferId: UUID) {
        val session = store.get(transferId) ?: return
        val remaining = session.fileSize - session.confirmedOffset
        val needed = if (remaining < 0L) 0L else remaining
        if (DiskPauseRule.resumeStayPaused(hasRoom(needed))) {
            session.status = ReceiveSession.PAUSED
            session.pausedBy = "TARGET"
            session.failureReason = DiskReserve.CODE
            persist(session)
            onProgress(session)
            throw ProtocolException(DiskReserve.CODE, DiskReserve.DETAIL, 507)
        }
        session.status = ReceiveSession.ACTIVE
        session.pausedBy = null
        session.failureReason = null
        persist(session)
        stallTracker.touch(transferId)
        onProgress(session)
        TransferDiskNotifier.cancel(context, transferId)
    }

    /**
     * Cancels every live session and deletes its pending entry.
     */
    fun abortAll() {
        for (session in store.all().toList()) {
            abort(session, ReceiveSession.CANCELLED, "stopped")
            onProgress(session)
        }
    }

    /**
     * Drops pending Downloads rows with no session.
     */
    fun sweepOrphans() {
        val live = store.all().mapNotNull { it.mediaUri }.toSet()
        publisher.deletePendingExcept(live)
    }

    private fun rejectIfBusy(except: Set<UUID>) {
        val live = store.all().any { session ->
            session.transferId !in except &&
                (
                    session.status == ReceiveSession.ACTIVE ||
                        session.status == ReceiveSession.PAUSED ||
                        session.status == ReceiveSession.PENDING
                    )
        }
        if (live) {
            throw ProtocolException(
                "RECEIVE_IN_PROGRESS",
                "Another receive is already active",
                409,
            )
        }
    }

    private fun abort(session: ReceiveSession, status: String, reason: String?) {
        session.status = status
        session.failureReason = reason
        session.mediaUri?.let { publisher.delete(it) }
        store.remove(session.transferId)
        prefs.clearReceive(session.transferId.toString())
        stallTracker.remove(session.transferId)
        TransferDiskNotifier.cancel(context, session.transferId)
    }

    /**
     * True when the Downloads volume covers needed plus reserve.
     */
    private fun hasRoom(bytesNeeded: Long): Boolean {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return DiskReserveRule.allowed(DiskSpace.usableBytes(dir), bytesNeeded, reserveProvider.getBytes())
    }

    /**
     * Pauses TARGET, keeps the pending row, tells the sender.
     */
    private fun pauseForDisk(session: ReceiveSession) {
        session.status = ReceiveSession.PAUSED
        session.pausedBy = "TARGET"
        session.failureReason = DiskReserve.CODE
        persist(session)
        stallTracker.remove(session.transferId)
        onProgress(session)
        TransferDiskNotifier.show(context, session.transferId)
        notifySenderPause(session)
    }

    /**
     * Best-effort pause-notify to the sender.
     */
    private fun notifySenderPause(session: ReceiveSession) {
        val tokens = tokenStore.load()
        val ip = session.sourceIp
        val port = session.sourcePort
        if (tokens == null || ip.isNullOrBlank() || port == null) {
            return
        }
        try {
            val listed = lookupPeer(ip, port, tokens.publicToken)
            peerClient.notifyPause(
                ip,
                port,
                session.transferId,
                tokens.publicToken,
                tokens.publicId,
                "TARGET",
                DiskReserve.CODE,
                listed?.tlsFingerprint,
                listed?.tlsPort,
            )
        } catch (_: Exception) {
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

    /**
     * Writes session fields so a restart can find the pending URI.
     */
    private fun persist(session: ReceiveSession) {
        val json = JSONObject()
            .put("fileName", session.fileName)
            .put("fileSize", session.fileSize)
            .put("confirmedOffset", session.confirmedOffset)
            .put("status", session.status)
            .put("sourceDeviceId", session.sourceDeviceId ?: JSONObject.NULL)
            .put("sourceIp", session.sourceIp ?: JSONObject.NULL)
            .put("sourcePort", session.sourcePort ?: JSONObject.NULL)
            .put("batchTransferId", session.batchTransferId?.toString() ?: JSONObject.NULL)
            .put("mediaUri", session.mediaUri?.toString() ?: JSONObject.NULL)
            .put("pausedBy", session.pausedBy ?: JSONObject.NULL)
            .put("failureReason", session.failureReason ?: JSONObject.NULL)
        prefs.saveReceive(session.transferId.toString(), json.toString())
    }

    /**
     * Reloads pending sessions after a process restart.
     */
    private fun restorePersisted() {
        for ((idText, raw) in prefs.loadReceives()) {
            val id = try {
                UUID.fromString(idText)
            } catch (_: Exception) {
                continue
            }
            val json = try {
                JSONObject(raw)
            } catch (_: Exception) {
                continue
            }
            val uriText = json.optString("mediaUri", "")
            val uri = if (uriText.isBlank()) null else Uri.parse(uriText)
            if (uri != null && !publisher.existsPending(uri)) {
                prefs.clearReceive(idText)
                continue
            }
            val session = ReceiveSession(
                transferId = id,
                fileName = json.optString("fileName"),
                fileSize = json.optLong("fileSize"),
                sourceDeviceId = json.optString("sourceDeviceId").ifBlank { null },
                sourceIp = json.optString("sourceIp").ifBlank { null },
                sourcePort = if (json.has("sourcePort") && !json.isNull("sourcePort")) {
                    json.getInt("sourcePort")
                } else {
                    null
                },
                batchTransferId = json.optString("batchTransferId").ifBlank { null }?.let {
                    UUID.fromString(it)
                },
                tempFile = File(context.filesDir, "$id.part"),
                mediaUri = uri,
                confirmedOffset = json.optLong("confirmedOffset"),
                status = json.optString("status", ReceiveSession.ACTIVE),
                pausedBy = json.optString("pausedBy").ifBlank { null },
                failureReason = json.optString("failureReason").ifBlank { null },
            )
            store.put(session)
            if (session.status == ReceiveSession.ACTIVE) {
                stallTracker.touch(id)
            }
        }
    }

    data class ChunkAck(val confirmedOffset: Long, val complete: Boolean)

    data class BatchFile(
        val transferId: UUID,
        val fileName: String,
        val fileSize: Long,
        val sourceDeviceId: String?,
        val sourceIp: String?,
        val sourcePort: Int?,
        val batchTransferId: UUID?,
    )
}
