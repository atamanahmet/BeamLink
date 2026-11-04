package com.beamlink.peer.net

import com.beamlink.peer.AppGraph
import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.security.NexusTlsPin
import com.beamlink.peer.security.PeerTlsPin
import com.beamlink.peer.security.PinnedLeafTrust
import com.beamlink.peer.send.ConnectPhaseListener
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext

/**
 * Single OkHttp HTTP helper. No business logic.
 */
class HttpJsonClient(
    private val client: OkHttpClient = sharedClient,
) {

    data class Response(
        val status: Int,
        val body: String,
    )

    private val activeCall = AtomicReference<Call?>(null)

    /**
     * Sends JSON POST and returns status plus body text.
     */
    fun postJson(
        url: String,
        json: JSONObject,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int,
    ): Response {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        return exchange("POST", url, body, headers, timeoutMs, "application/json")
    }

    /**
     * Sends GET and returns status plus body text.
     */
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int,
    ): Response {
        return exchange("GET", url, null, headers, timeoutMs, null)
    }

    /**
     * Sends DELETE with optional headers.
     */
    fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeoutMs: Int,
    ): Response {
        return exchange("DELETE", url, null, headers, timeoutMs, null)
    }

    /**
     * Sends a real PATCH with raw bytes.
     */
    fun patchBytes(
        url: String,
        body: ByteArray,
        headers: Map<String, String>,
        timeoutMs: Int,
    ): Response {
        return exchange("PATCH", url, body, headers, timeoutMs, "application/octet-stream")
    }

    /**
     * Aborts the in-flight OkHttp Call if any.
     */
    fun cancelActiveCall() {
        activeCall.getAndSet(null)?.cancel()
    }

    private fun exchange(
        method: String,
        url: String,
        body: ByteArray?,
        headers: Map<String, String>,
        timeoutMs: Int,
        contentType: String?,
    ): Response {
        val builder = Request.Builder().url(url)
        for ((k, v) in headers) {
            builder.header(k, v)
        }
        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val media = (contentType ?: "application/json").toMediaType()
                builder.post((body ?: ByteArray(0)).toRequestBody(media))
            }
            "PATCH" -> {
                val media = (contentType ?: "application/octet-stream").toMediaType()
                builder.patch((body ?: ByteArray(0)).toRequestBody(media))
            }
            "DELETE" -> builder.delete()
            else -> throw IllegalArgumentException("Unsupported method: $method")
        }
        val call = client.newBuilder()
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
            .newCall(builder.build())
        activeCall.set(call)
        try {
            call.execute().use { response ->
                val text = response.body?.string() ?: ""
                return Response(response.code, text)
            }
        } catch (e: IOException) {
            if (call.isCanceled()) {
                throw IOException("Call cancelled", e)
            }
            throw e
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    companion object {
        private val sharedClient: OkHttpClient = buildSharedClient()

        /**
         * Builds the one app-wide OkHttp client.
         */
        fun buildSharedClient(): OkHttpClient {
            val pin = NexusTlsPin {
                if (!AppGraph.ready) {
                    null
                } else {
                    AppGraph.prefs.tlsPin.ifBlank { null }
                }
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(pin), SecureRandom())
            return OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .sslSocketFactory(ctx.socketFactory, pin)
                .hostnameVerifier(pin)
                .connectTimeout(DeviceDefaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(DeviceDefaults.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(DeviceDefaults.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(DeviceDefaults.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(AddressGuardInterceptor())
                .build()
        }

        /**
         * OkHttp client that trusts one listed peer pin and skips hostname checks.
         */
        fun pinnedClient(pin: String): OkHttpClient {
            val trust = PinnedLeafTrust(
                { pin },
                PeerTlsPin.MISSING_PIN,
                PeerTlsPin.MISMATCH,
            )
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(trust), SecureRandom())
            return OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .sslSocketFactory(ctx.socketFactory, trust)
                .hostnameVerifier(trust)
                .connectTimeout(DeviceDefaults.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(DeviceDefaults.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(DeviceDefaults.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(DeviceDefaults.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .eventListenerFactory(ConnectPhaseListener.Factory)
                .addInterceptor(ConnectTimeoutInterceptor())
                .addInterceptor(AddressGuardInterceptor())
                .build()
        }

        /**
         * Parses a JSON object body or throws.
         */
        fun parseObject(body: String): JSONObject {
            if (body.isBlank()) {
                throw IllegalStateException("Empty JSON body")
            }
            return JSONObject(body)
        }

        /**
         * Parses a JSON array body or throws.
         */
        fun parseArray(body: String): JSONArray {
            if (body.isBlank()) {
                throw IllegalStateException("Empty JSON array")
            }
            return JSONArray(body)
        }
    }
}
