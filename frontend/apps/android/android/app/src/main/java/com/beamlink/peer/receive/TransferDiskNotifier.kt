package com.beamlink.peer.receive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.beamlink.peer.MainActivity
import com.beamlink.peer.R
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One disk-pause notification per receive transfer.
 */
object TransferDiskNotifier {

    const val CHANNEL_ID = "transfers"
    const val EXTRA_OPEN_TRANSFERS = "com.beamlink.OPEN_TRANSFERS"

    private val shown = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * Shows the mapper text if this transfer has no notification yet.
     */
    fun show(context: Context, transferId: UUID) {
        if (!shown.add(transferId)) {
            return
        }
        val app = context.applicationContext
        ensureChannel(app)
        val tap = PendingIntent.getActivity(
            app,
            notifyId(transferId),
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_TRANSFERS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val copy = splitDetail(DiskReserve.DETAIL)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hex)
            .setContentTitle(copy.first)
            .setContentText(copy.second)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(DiskReserve.DETAIL),
            )
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.notify(notifyId(transferId), notification)
    }

    /**
     * Drops the notification when the transfer resumes or is cancelled.
     */
    fun cancel(context: Context, transferId: UUID) {
        shown.remove(transferId)
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        manager.cancel(notifyId(transferId))
    }

    /**
     * Creates channel transfers if missing.
     */
    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transfers",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    private fun notifyId(transferId: UUID): Int {
        return transferId.hashCode()
    }

    /**
     * Title is the first sentence of the mapper text.
     */
    private fun splitDetail(text: String): Pair<String, String> {
        val i = text.indexOf(". ")
        if (i < 0) {
            return text to ""
        }
        return text.substring(0, i + 1) to text.substring(i + 2)
    }
}
