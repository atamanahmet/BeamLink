package com.beamlink.peer.receive

import java.io.File
import java.util.UUID

/**
 * In-memory receive row for one inbound file.
 */
data class ReceiveSession(
    val transferId: UUID,
    val fileName: String,
    val fileSize: Long,
    val sourceDeviceId: String?,
    val sourceIp: String?,
    val sourcePort: Int?,
    val batchTransferId: UUID?,
    val tempFile: File,
    var mediaUri: android.net.Uri? = null,
    var confirmedOffset: Long = 0L,
    var status: String = ACTIVE,
    var pausedBy: String? = null,
    var failureReason: String? = null,
) {
    companion object {
        const val ACTIVE = "ACTIVE"
        const val PAUSED = "PAUSED"
        const val COMPLETED = "COMPLETED"
        const val CANCELLED = "CANCELLED"
        const val FAILED = "FAILED"
        const val PENDING = "PENDING"
    }
}
