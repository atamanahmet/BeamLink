package com.beamlink.peer.net

import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.register.NexusPrefs
import com.beamlink.peer.security.TokenStore
import com.beamlink.peer.util.WifiIpv4
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nexus /ws/devices client. Runs only while receive is on.
 */
class NexusWebSocket(
    private val prefs: NexusPrefs,
    private val tokenStore: TokenStore,
    private val onAuthLost: (() -> Unit)? = null,
    private val client: OkHttpClient = HttpJsonClient.buildSharedClient(),
) {

    private val running = AtomicBoolean(false)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var socket: WebSocket? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var status: ScheduledFuture<*>? = null
    private var reconnect: ScheduledFuture<*>? = null

    /**
     * Opens the socket when tokens exist.
     */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            return
        }
        connect()
    }

    /**
     * Closes the socket and cancels timers.
     */
    fun stop() {
        running.set(false)
        heartbeat?.cancel(false)
        status?.cancel(false)
        reconnect?.cancel(false)
        heartbeat = null
        status = null
        reconnect = null
        socket?.cancel()
        socket = null
    }

    private fun connect() {
        if (!running.get()) return
        val tokens = tokenStore.load()
        val nexusUrl = prefs.nexusUrl
        if (tokens == null || nexusUrl.isBlank() || prefs.tlsPin.isBlank()) {
            scheduleReconnect()
            return
        }
        val wsUrl = nexusUrl.trimEnd('/')
            .replaceFirst(Regex("^http://"), "ws://")
            .replaceFirst(Regex("^https://"), "wss://") + "/ws/devices"
        val request = Request.Builder()
            .url(wsUrl)
            .header("X-Auth-Token", tokens.authToken)
            .build()
        socket = client.newWebSocket(request, Listener())
    }

    private fun scheduleReconnect() {
        if (!running.get()) return
        reconnect?.cancel(false)
        reconnect = scheduler.schedule({ connect() }, 10, TimeUnit.SECONDS)
    }

    private fun startTimers(ws: WebSocket) {
        heartbeat?.cancel(false)
        status?.cancel(false)
        heartbeat = scheduler.scheduleAtFixedRate({
            if (!running.get()) return@scheduleAtFixedRate
            val body = JSONObject()
                .put("type", "HEARTBEAT")
                .put("payload", JSONObject().put("peerVersion", 0))
            ws.send(body.toString())
        }, 15, 15, TimeUnit.SECONDS)
        status = scheduler.scheduleAtFixedRate({
            if (!running.get()) return@scheduleAtFixedRate
            sendStatus(ws)
        }, 30, 30, TimeUnit.SECONDS)
    }

    /**
     * Refreshes Wi-Fi IPv4 then sends STATUS_UPDATE.
     */
    private fun sendStatus(ws: WebSocket) {
        val ip = try {
            WifiIpv4.resolve()
        } catch (_: Exception) {
            prefs.registeredIp
        }
        if (ip.isNotBlank()) {
            prefs.registeredIp = ip
        }
        val body = JSONObject()
            .put("type", "STATUS_UPDATE")
            .put(
                "payload",
                JSONObject()
                    .put("ipAddress", ip)
                    .put("port", DeviceDefaults.DEVICE_PORT)
                    .put("peerVersion", 0),
            )
        ws.send(body.toString())
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            startTimers(webSocket)
            sendStatus(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeat?.cancel(false)
            status?.cancel(false)
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            heartbeat?.cancel(false)
            status?.cancel(false)
            val code = response?.code
            if (code == 401 || code == 403) {
                onAuthLost?.invoke()
            }
            scheduleReconnect()
        }
    }
}
