package com.beamlink.peer.receive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.beamlink.peer.AppGraph
import com.beamlink.peer.R
import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.net.NexusWebSocket
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground dataSync receive: Ktor :9090, WifiLock, WS while on.
 */
class ReceiveForegroundService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var webSocket: NexusWebSocket? = null
    private var stallExecutor: ScheduledExecutorService? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopInternal()
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        acquireWifiLock()
        AppGraph.init(this)
        try {
            AppGraph.chunkReceiver.sweepOrphans()
            AppGraph.receiveServer.start()
            running = true
            emitToggle(true, "")
        } catch (e: Exception) {
            running = false
            AppGraph.prefs.receiveEnabled = false
            emitToggle(false, "Receive bind failed: ${e.message ?: "port in use"}")
            stopInternal()
            stopSelf()
            return START_NOT_STICKY
        }
        if (webSocket == null) {
            webSocket = NexusWebSocket(
                AppGraph.prefs,
                AppGraph.tokenStore,
                onAuthLost = { AppGraph.onAuthLost?.invoke() },
            )
            webSocket?.start()
        }
        startStallWatchdog()
        return START_STICKY
    }

    /**
     * Stops receive when Android ends the dataSync time window.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        AppGraph.prefs.receiveEnabled = false
        emitToggle(false, "Receive stopped: Android time limit")
        stopInternal()
        postTimeoutNotice()
        stopSelf()
    }

    override fun onDestroy() {
        stopInternal()
        super.onDestroy()
    }

    /**
     * Stops the server, WS, and WifiLock.
     */
    private fun stopInternal() {
        running = false
        try {
            if (AppGraph.ready) {
                AppGraph.chunkReceiver.abortAll()
            }
        } catch (_: Exception) {
        }
        try {
            AppGraph.receiveServer.stop()
        } catch (_: Exception) {
        }
        stopStallWatchdog()
        webSocket?.stop()
        webSocket = null
        wifiLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wifiLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Checks receive idle stalls every 15 seconds. Does not stop this service.
     */
    private fun startStallWatchdog() {
        if (stallExecutor != null) {
            return
        }
        val executor = Executors.newSingleThreadScheduledExecutor()
        stallExecutor = executor
        executor.scheduleWithFixedDelay(
            {
                try {
                    if (AppGraph.ready) {
                        AppGraph.receiveStallWatchdog.checkStalls()
                    }
                } catch (_: Exception) {
                }
            },
            ReceiveStallWatchdog.CHECK_INTERVAL_MS,
            ReceiveStallWatchdog.CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * Stops the stall checker.
     */
    private fun stopStallWatchdog() {
        stallExecutor?.shutdownNow()
        stallExecutor = null
    }

    /**
     * Holds Wi-Fi with WIFI_MODE_FULL_LOW_LATENCY.
     */
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createWifiLock(
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
            "beamlink:receive",
        )
        lock.setReferenceCounted(false)
        lock.acquire()
        wifiLock = lock
    }

    /**
     * Shows the ongoing receive notification.
     */
    private fun startAsForeground() {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeamLink")
            .setContentText("Receiving on :${DeviceDefaults.DEVICE_PORT}")
            .setSmallIcon(R.drawable.ic_stat_hex)
            .setOngoing(true)
            .build()
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    /**
     * Creates the receive notification channel.
     */
    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Receive",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the Android time-limit notice on the receive channel.
     */
    private fun postTimeoutNotice() {
        ensureChannel()
        val text =
            "Receiving was turned off by Android after a long time. Open the app to turn it on again."
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

    companion object {
        const val ACTION_STOP = "com.beamlink.peer.STOP_RECEIVE"
        private const val CHANNEL_ID = "beamlink_receive"
        private const val NOTIFICATION_ID = 47
        private const val TIMEOUT_NOTICE_ID = 49

        @Volatile
        var running: Boolean = false

        @Volatile
        var toggleListener: ((enabled: Boolean, message: String) -> Unit)? = null

        /**
         * Tells the plugin the receive switch flipped.
         */
        fun emitToggle(enabled: Boolean, message: String) {
            toggleListener?.invoke(enabled, message)
        }

        /**
         * Starts the receive foreground service.
         */
        fun start(context: Context) {
            val intent = Intent(context, ReceiveForegroundService::class.java)
            context.startForegroundService(intent)
        }

        /**
         * Stops the receive foreground service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, ReceiveForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
