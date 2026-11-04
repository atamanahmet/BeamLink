package com.beamlink.peer.send

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import com.beamlink.peer.R
import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.event.ProgressBus
import com.beamlink.peer.net.PeerTransferClient
import com.beamlink.peer.receive.DiskReserve
import com.beamlink.peer.security.TokenStore
import com.beamlink.peer.util.WifiIpv4
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground dataSync service so send survives the screen turning off.
 */
class TransferForegroundService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pauseReason = AtomicReference<String?>(null)
    private val peerClient = PeerTransferClient()
    private var lastName: String? = null
    private var lastPeer: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelled.set(true)
                peerClient.cancel()
                pausedJob = null
                val slot = ProgressBus.snapshot().find { it.direction == "send" }
                if (slot != null && slot.state != "completed" && slot.state != "cancelled") {
                    lastName = slot.name
                    lastPeer = slot.peer
                    emitProgress(slot.bytes, slot.total, "cancelled", "Cancelled")
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SEND -> {
                val job = SendJob(
                    uris = intent.getStringArrayListExtra(EXTRA_URIS) ?: arrayListOf(),
                    names = intent.getStringArrayListExtra(EXTRA_NAMES) ?: arrayListOf(),
                    sizes = intent.getLongArrayExtra(EXTRA_SIZES) ?: LongArray(0),
                    targetIp = intent.getStringExtra(EXTRA_TARGET_IP) ?: "",
                    targetPort = intent.getIntExtra(EXTRA_TARGET_PORT, 0),
                    targetDeviceId = intent.getStringExtra(EXTRA_TARGET_DEVICE_ID) ?: "",
                    tlsFingerprint = intent.getStringExtra(EXTRA_TLS_FINGERPRINT),
                    tlsPort = intent.getIntExtra(EXTRA_TLS_PORT, -1).takeIf { it > 0 },
                )
                beginSend(job, resume = false)
            }
            ACTION_RESUME -> {
                val job = pausedJob
                if (job == null || sending.get()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                beginSend(job, resume = true)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Starts foreground send on the worker thread. Caller must be in the foreground.
     */
    private fun beginSend(job: SendJob, resume: Boolean) {
        if (!sending.compareAndSet(false, true)) {
            return
        }
        cancelled.set(false)
        paused.set(false)
        pauseReason.set(null)
        pauseHandler = this::onPeerPause
        if (!resume) {
            pausedJob = null
        }
        startAsForeground()
        executor.execute {
            try {
                runSend(job, resume)
            } finally {
                sending.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * Pauses the send and drops foreground when Android ends the dataSync window.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        pauseReason.set(
            "Android stopped background sending after a long time. Open the app to check the transfer.",
        )
        paused.set(true)
        cancelled.set(true)
        peerClient.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        postTimeoutNotice()
        stopSelf()
    }

    /**
     * Sends files in order. Resume starts at the saved file and receiver offset.
     */
    private fun runSend(job: SendJob, resume: Boolean) {
        val tokens = TokenStore(this).load()
        if (tokens == null) {
            emitProgress(0L, 0L, "error", "Waiting for approval. Tokens not available.")
            pausedJob = null
            return
        }
        val uris = job.uris
        val names = job.names
        val sizes = job.sizes
        var total = 0L
        for (i in uris.indices) {
            total += if (i < sizes.size) sizes[i] else 0L
        }
        if (total <= 0L) {
            total = uris.size.toLong()
        }
        val startIndex = if (resume) job.fileIndex.coerceIn(0, uris.size) else 0
        if (!resume) {
            job.fileIndex = 0
            job.transferId = null
        }
        var sent = 0L
        for (j in 0 until startIndex) {
            sent += if (j < sizes.size) sizes[j] else 0L
        }
        lastPeer = job.targetDeviceId
        lastName = names.getOrNull(startIndex) ?: names.firstOrNull()
        emitProgress(sent, total, "sending", null)
        try {
            val sourceIp = WifiIpv4.resolve()
            for (i in startIndex until uris.size) {
                if (cancelled.get()) {
                    if (!paused.get()) {
                        pausedJob = null
                    } else {
                        pausedJob = job
                    }
                    emitProgress(
                        sent,
                        total,
                        if (paused.get()) "paused" else "cancelled",
                        if (paused.get()) pauseText(pauseReason.get()) else "Cancelled",
                    )
                    return
                }
                val uri = Uri.parse(uris[i])
                val name = names.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "file"
                lastName = name
                val size = if (i < sizes.size && sizes[i] > 0) {
                    sizes[i]
                } else {
                    querySize(uri)
                }
                val fileBase = sent
                val resumeId = if (resume && i == startIndex) job.transferId else null
                val transferId = resumeId ?: UUID.randomUUID()
                job.fileIndex = i
                job.transferId = transferId
                if (SendResumeRule.skipRegister(resumeId)) {
                    try {
                        peerClient.notifyResume(
                            job.targetIp,
                            job.targetPort,
                            transferId,
                            tokens.publicToken,
                            tokens.publicId,
                            job.tlsFingerprint,
                            job.tlsPort,
                        )
                    } catch (e: PeerTransferClient.ChunkHttpException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
                val input = try {
                    contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    if (SourceReopenRule.cannotReopen(null, e)) {
                        pausedJob = null
                        emitProgress(sent, total, "error", SourceReopenRule.DETAIL)
                        return
                    }
                    throw e
                }
                if (SourceReopenRule.cannotReopen(input, null) || input == null) {
                    pausedJob = null
                    emitProgress(sent, total, "error", SourceReopenRule.DETAIL)
                    return
                }
                input.use { stream ->
                    peerClient.sendFile(
                        targetIp = job.targetIp,
                        targetPort = job.targetPort,
                        sourceDeviceId = tokens.deviceId,
                        publicToken = tokens.publicToken,
                        publicId = tokens.publicId,
                        fileName = name,
                        fileSize = size,
                        sourceIp = sourceIp,
                        sourcePort = DeviceDefaults.DEVICE_PORT,
                        readChunk = { buffer -> stream.read(buffer) },
                        onProgress = { fileSent ->
                            sent = fileBase + fileSent
                            emitProgress(sent, total, "sending", null)
                        },
                        isCancelled = { cancelled.get() },
                        tlsFingerprint = job.tlsFingerprint,
                        tlsPort = job.tlsPort,
                        transferId = transferId,
                        skipRegister = SendResumeRule.skipRegister(resumeId),
                    )
                }
                sent = fileBase + size
                emitProgress(sent, total, "sending", null)
            }
            pausedJob = null
            emitProgress(total, total, "completed", null)
        } catch (e: PeerTransferClient.CancelledException) {
            if (paused.get()) {
                pausedJob = job
                emitProgress(sent, total, "paused", pauseText(pauseReason.get()))
            } else {
                pausedJob = null
                emitProgress(sent, total, "cancelled", "Cancelled")
            }
        } catch (e: PeerTransferClient.ChunkHttpException) {
            if (e.statusCode == 507) {
                pausedJob = job
                emitProgress(sent, total, "paused", DiskReserve.DETAIL)
            } else {
                pausedJob = null
                emitProgress(sent, total, "error", e.message ?: "Send failed")
            }
        } catch (e: Exception) {
            if (paused.get()) {
                pausedJob = job
                emitProgress(sent, total, "paused", pauseText(pauseReason.get()))
            } else {
                pausedJob = null
                emitProgress(
                    sent,
                    total,
                    "error",
                    ConnectTimeoutText.of(e, job.targetIp) ?: e.message ?: "Send failed",
                )
            }
        }
    }

    /**
     * Stops the in-flight send when the receiver pause-notify matches.
     */
    private fun onPeerPause(transferId: UUID, reason: String?) {
        if (peerClient.activeTransferId != transferId) {
            return
        }
        pauseReason.set(reason)
        paused.set(true)
        cancelled.set(true)
        peerClient.cancel()
    }

    /**
     * Maps INSUFFICIENT_DISK to the shared mapper text.
     */
    private fun pauseText(reason: String?): String {
        if (reason.isNullOrBlank() || reason == DiskReserve.CODE) {
            return DiskReserve.DETAIL
        }
        return reason
    }

    private fun querySize(uri: Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                return cursor.getLong(sizeIndex)
            }
        }
        return 0L
    }

    private fun startAsForeground() {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeamLink")
            .setContentText("Sending files…")
            .setSmallIcon(R.drawable.ic_stat_hex)
            .setOngoing(true)
            .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transfers",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the Android time-limit notice on the send channel.
     */
    private fun postTimeoutNotice() {
        ensureChannel()
        val text =
            "Android stopped background sending after a long time. Open the app to check the transfer."
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeamLink")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_hex)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(TIMEOUT_NOTICE_ID, notification)
    }

    private fun emitProgress(bytes: Long, total: Long, state: String, error: String?) {
        progressListener?.invoke(bytes, total, state, error)
        ProgressBus.emit(bytes, total, state, error, "send", lastName, lastPeer)
    }

    companion object {
        const val ACTION_SEND = "com.beamlink.peer.SEND"
        const val ACTION_RESUME = "com.beamlink.peer.RESUME_SEND"
        const val ACTION_CANCEL = "com.beamlink.peer.CANCEL_SEND"

        @Volatile
        private var pausedJob: SendJob? = null

        private val sending = AtomicBoolean(false)
        const val EXTRA_URIS = "uris"
        const val EXTRA_NAMES = "names"
        const val EXTRA_SIZES = "sizes"
        const val EXTRA_TARGET_IP = "targetIp"
        const val EXTRA_TARGET_PORT = "targetPort"
        const val EXTRA_TARGET_DEVICE_ID = "targetDeviceId"
        const val EXTRA_TLS_FINGERPRINT = "tlsFingerprint"
        const val EXTRA_TLS_PORT = "tlsPort"

        private const val CHANNEL_ID = "beamlink_transfers"
        private const val NOTIFICATION_ID = 46
        private const val TIMEOUT_NOTICE_ID = 48

        @Volatile
        var progressListener: ((Long, Long, String, String?) -> Unit)? = null

        @Volatile
        var pauseHandler: ((UUID, String?) -> Unit)? = null

        /**
         * Applies pause-notify to the live send slot when the id matches.
         */
        fun applyPeerPause(transferId: UUID, reason: String?) {
            pauseHandler?.invoke(transferId, reason)
        }

        /**
         * Starts the foreground send.
         */
        fun startSend(
            context: Context,
            uris: ArrayList<String>,
            names: ArrayList<String>,
            sizes: LongArray,
            targetIp: String,
            targetPort: Int,
            targetDeviceId: String,
            tlsFingerprint: String?,
            tlsPort: Int?,
        ) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_SEND
                putStringArrayListExtra(EXTRA_URIS, uris)
                putStringArrayListExtra(EXTRA_NAMES, names)
                putExtra(EXTRA_SIZES, sizes)
                putExtra(EXTRA_TARGET_IP, targetIp)
                putExtra(EXTRA_TARGET_PORT, targetPort)
                putExtra(EXTRA_TARGET_DEVICE_ID, targetDeviceId)
                putExtra(EXTRA_TLS_FINGERPRINT, tlsFingerprint)
                putExtra(EXTRA_TLS_PORT, tlsPort ?: -1)
            }
            context.startForegroundService(intent)
        }

        /**
         * Requests cancel of the active send.
         */
        fun cancel(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        /**
         * True when a paused send can restart from the saved job.
         */
        fun hasPausedJob(): Boolean {
            return pausedJob != null && !sending.get()
        }

        /**
         * Restarts the paused send. Caller must be in the foreground.
         */
        fun resume(context: Context) {
            val intent = Intent(context, TransferForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startForegroundService(intent)
        }
    }
}

/**
 * Saved send so a paused job can restart from the receiver offset.
 */
private data class SendJob(
    val uris: ArrayList<String>,
    val names: ArrayList<String>,
    val sizes: LongArray,
    val targetIp: String,
    val targetPort: Int,
    val targetDeviceId: String,
    val tlsFingerprint: String?,
    val tlsPort: Int?,
    var fileIndex: Int = 0,
    var transferId: UUID? = null,
)
