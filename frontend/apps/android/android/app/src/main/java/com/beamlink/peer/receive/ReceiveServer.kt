package com.beamlink.peer.receive

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.JWTVerifier
import android.util.Log
import com.beamlink.peer.config.DeviceDefaults
import com.beamlink.peer.exception.InvalidRequestException
import com.beamlink.peer.exception.ProblemStatusMapper
import com.beamlink.peer.net.AddressGuard
import com.beamlink.peer.security.PhoneTlsCert
import com.beamlink.peer.security.TokenStore
import com.beamlink.peer.send.TransferForegroundService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.json.JSONException
import org.json.JSONObject
import java.net.InetAddress
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import javax.net.ssl.KeyManagerFactory

/**
 * Ktor Netty TLS receive server on the device port.
 */
class ReceiveServer(
    private val receiver: ChunkReceiver,
    private val tokenStore: TokenStore,
    private val phoneTlsCert: PhoneTlsCert,
) {

    private var engine: EmbeddedServer<*, *>? = null

    /**
     * Binds TLS on 0.0.0.0:9090. Throws when the port is already taken.
     */
    fun start() {
        if (engine != null) return
        phoneTlsCert.loadOrCreate()
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(phoneTlsCert.keyStore(), null)
        val sslContext = SslContextBuilder.forServer(kmf).build()
        val server = embeddedServer(Netty, configure = {
            connector {
                host = "0.0.0.0"
                port = DeviceDefaults.DEVICE_PORT
            }
            channelPipelineConfig = {
                val sslEngine = sslContext.newEngine(channel().alloc())
                sslEngine.useClientMode = false
                addFirst("ssl", SslHandler(sslEngine))
            }
        }) {
            intercept(ApplicationCallPipeline.Setup) {
                val host = call.request.origin.remoteHost
                val allowed = try {
                    AddressGuard.isAllowedAddress(InetAddress.getByName(host))
                } catch (_: Exception) {
                    false
                }
                if (!allowed) {
                    call.respondProblem(
                        403,
                        ProtocolErrorBodies.of(
                            403,
                            "Forbidden",
                            "Remote address refused",
                            "FORBIDDEN",
                        ),
                    )
                    finish()
                }
            }
            intercept(ApplicationCallPipeline.Plugins) {
                val path = call.request.path()
                if (path.startsWith("/api/transfers")) {
                    val key = tokenStore.load()?.nexusPublicKey
                    if (key.isNullOrBlank()) {
                        call.respondProblem(
                            503,
                            ProtocolErrorBodies.of(
                                503,
                                "Not approved",
                                "Not yet approved by nexus",
                                "NOT_APPROVED",
                            ),
                        )
                        finish()
                    }
                }
            }
            install(StatusPages) {
                exception<ProtocolException> { call, cause ->
                    call.respondProblem(cause.status, ProtocolErrorBodies.toJson(cause))
                }
                exception<InvalidRequestException> { call, cause ->
                    val mapped = ProblemStatusMapper.map(cause)
                    call.respondProblem(
                        mapped.status,
                        ProtocolErrorBodies.of(
                            mapped.status,
                            "Bad request",
                            cause.message ?: "Invalid request",
                            mapped.code,
                        ),
                    )
                }
                exception<JSONException> { call, cause ->
                    val mapped = ProblemStatusMapper.map(cause)
                    call.respondProblem(
                        mapped.status,
                        ProtocolErrorBodies.of(
                            mapped.status,
                            "Bad request",
                            "Malformed request body",
                            mapped.code,
                        ),
                    )
                }
                exception<IllegalArgumentException> { call, cause ->
                    logServerError(cause)
                    val mapped = ProblemStatusMapper.map(cause)
                    call.respondProblem(
                        mapped.status,
                        ProtocolErrorBodies.of(
                            mapped.status,
                            "Internal error",
                            "An unexpected error occurred",
                            mapped.code,
                        ),
                    )
                }
                exception<Throwable> { call, cause ->
                    if (cause is kotlin.coroutines.cancellation.CancellationException) {
                        throw cause
                    }
                    logServerError(cause)
                    val mapped = ProblemStatusMapper.map(cause)
                    call.respondProblem(
                        mapped.status,
                        ProtocolErrorBodies.of(
                            mapped.status,
                            "Internal error",
                            "An unexpected error occurred",
                            mapped.code,
                        ),
                    )
                }
            }
            install(Authentication) {
                jwt("peer") {
                    authHeader { call ->
                        val token = call.request.header("X-Public-Token")
                        if (token.isNullOrBlank()) {
                            null
                        } else {
                            HttpAuthHeader.Single("Bearer", token)
                        }
                    }
                    verifier(DynamicPeerVerifier(tokenStore))
                    validate { cred ->
                        val type = cred.payload.getClaim("type").asString()
                        if (type != "DEVICE_PUBLIC") {
                            return@validate null
                        }
                        val claimId = cred.payload.getClaim("publicId").asString()
                        val headerId = request.header("X-Public-Id")
                        if (claimId.isNullOrBlank() || headerId.isNullOrBlank() || claimId != headerId) {
                            return@validate null
                        }
                        try {
                            UUID.fromString(headerId)
                        } catch (_: Exception) {
                            return@validate null
                        }
                        JWTPrincipal(cred.payload)
                    }
                    challenge { _, _ ->
                        val token = call.request.header("X-Public-Token")
                        val publicId = call.request.header("X-Public-Id")
                        when {
                            token.isNullOrBlank() || publicId.isNullOrBlank() ->
                                call.respondProblem(
                                    401,
                                    ProtocolErrorBodies.of(
                                        401,
                                        "Unauthorized",
                                        "Missing peer token headers",
                                        "UNAUTHORIZED",
                                    ),
                                )
                            else ->
                                call.respondProblem(
                                    401,
                                    ProtocolErrorBodies.of(
                                        401,
                                        "Unauthorized",
                                        "Invalid peer token",
                                        "UNAUTHORIZED",
                                    ),
                                )
                        }
                    }
                }
            }
            routing {
                authenticate("peer") {
                    post("/api/transfers/receive") {
                        val json = JSONObject(call.receiveText())
                        receiver.prepare(
                            transferId = parseUuid(json.getString("transferId")),
                            fileName = json.getString("fileName"),
                            fileSize = FileSizeRule.parse(json),
                            sourceDeviceId = JsonStrings.optional(json, "sourceDeviceId"),
                            sourceIp = JsonStrings.optional(json, "sourceIp"),
                            sourcePort = if (json.has("sourcePort") && !json.isNull("sourcePort")) {
                                json.getInt("sourcePort")
                            } else {
                                null
                            },
                            batchTransferId = null,
                        )
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/api/transfers/receive-batch") {
                        val json = JSONObject(call.receiveText())
                        val filesJson = json.getJSONArray("files")
                        val files = ArrayList<ChunkReceiver.BatchFile>()
                        val batchId = parseUuid(json.getString("batchTransferId"))
                        val sourceDeviceId = JsonStrings.optional(json, "sourceDeviceId")
                        for (i in 0 until filesJson.length()) {
                            val item = filesJson.getJSONObject(i)
                            files.add(
                                ChunkReceiver.BatchFile(
                                    transferId = parseUuid(item.getString("transferId")),
                                    fileName = item.getString("fileName"),
                                    fileSize = FileSizeRule.parse(item),
                                    sourceDeviceId = sourceDeviceId,
                                    sourceIp = null,
                                    sourcePort = null,
                                    batchTransferId = batchId,
                                ),
                            )
                        }
                        receiver.prepareBatch(json.optLong("totalSize", 0L), files)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/api/transfers/receive-directory") {
                        call.respondProblem(
                            501,
                            ProtocolErrorBodies.of(
                                501,
                                "Transfer protocol error",
                                "Directory receive is not supported on Android yet",
                                "DIRECTORY_NOT_SUPPORTED",
                            ),
                        )
                    }
                    patch("/api/transfers/{transferId}/chunk") {
                        val id = parseUuid(call.parameters["transferId"])
                        val range = ContentRangeParser.parse(call.request.header("Content-Range"))
                        val sha = call.request.header(DeviceDefaults.CHUNK_SHA256_HEADER)
                        val body = call.receive<ByteArray>()
                        val ack = receiver.receiveChunk(id, range.start, range.length, body, sha)
                        call.respondJson(
                            200,
                            JSONObject()
                                .put("confirmedOffset", ack.confirmedOffset)
                                .put("complete", ack.complete),
                        )
                    }
                    get("/api/transfers/{transferId}/offset") {
                        val id = parseUuid(call.parameters["transferId"])
                        val offset = receiver.confirmedOffset(id)
                        call.respondJson(
                            200,
                            JSONObject().put("confirmedOffset", offset),
                        )
                    }
                    delete("/api/transfers/{transferId}/cancel-notify") {
                        val id = parseUuid(call.parameters["transferId"])
                        receiver.cancel(id)
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/api/transfers/{transferId}/fail-notify") {
                        val id = parseUuid(call.parameters["transferId"])
                        val json = JSONObject(call.receiveText().ifBlank { "{}" })
                        receiver.fail(id, JsonStrings.optional(json, "reason"))
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/api/transfers/{transferId}/pause-notify") {
                        val id = parseUuid(call.parameters["transferId"])
                        val json = JSONObject(call.receiveText().ifBlank { "{}" })
                        val pausedBy = json.optString("pausedBy", "")
                        if (pausedBy.isBlank()) {
                            throw InvalidRequestException("pausedBy required")
                        }
                        val reason = json.optString("reason", "").ifBlank { null }
                        if (!receiver.pause(id, pausedBy, reason)) {
                            TransferForegroundService.applyPeerPause(id, reason)
                        }
                        call.respond(HttpStatusCode.OK)
                    }
                    post("/api/transfers/{transferId}/resume-notify") {
                        val id = parseUuid(call.parameters["transferId"])
                        receiver.resume(id)
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
        try {
            server.start(wait = false)
            engine = server
        } catch (e: Exception) {
            try {
                server.stop(0L, 0L)
            } catch (_: Exception) {
            }
            engine = null
            throw e
        }
    }

    /**
     * Stops the engine if running.
     */
    fun stop() {
        engine?.stop(1000L, 2000L)
        engine = null
    }
}

/**
 * Writes a JSON body with an HTTP status.
 */
private suspend fun ApplicationCall.respondJson(status: Int, json: JSONObject) {
    respondText(
        json.toString(),
        ContentType.Application.Json,
        HttpStatusCode.fromValue(status),
    )
}

/**
 * Writes RFC 9457 JSON with application/problem+json.
 */
private suspend fun ApplicationCall.respondProblem(status: Int, json: JSONObject) {
    val cid = json.optString("correlationId", "")
    if (cid.isNotBlank()) {
        response.header("X-Correlation-Id", cid)
    }
    respondText(
        json.toString(),
        ContentType.parse(ProblemStatusMapper.CONTENT_TYPE),
        HttpStatusCode.fromValue(status),
    )
}

/**
 * Parses a transfer id. Bad text is 400.
 */
private fun parseUuid(text: String?): UUID {
    try {
        return UUID.fromString(text)
    } catch (e: Exception) {
        throw InvalidRequestException("Invalid transferId", e)
    }
}

/**
 * Stack log for 500 bodies. Matches Java UNKNOWN 5xx.
 */
private fun logServerError(cause: Throwable) {
    Log.e("ReceiveServer", "INTERNAL_ERROR", cause)
}

/**
 * Verifies peer JWTs with the stored nexus RSA key.
 */
private class DynamicPeerVerifier(
    private val tokenStore: TokenStore,
) : JWTVerifier {

    override fun verify(token: String): DecodedJWT {
        val keyB64 = tokenStore.load()?.nexusPublicKey
            ?: throw JWTVerificationException("Nexus public key missing")
        val key = loadRsa(keyB64)
        val decoded = JWT.require(Algorithm.RSA256(key, null))
            .withIssuer("beamlink-nexus")
            .build()
            .verify(token)
        if (decoded.expiresAt == null) {
            throw JWTVerificationException("Token does not contain exp claim")
        }
        return decoded
    }

    override fun verify(jwt: DecodedJWT): DecodedJWT {
        return verify(jwt.token)
    }

    /**
     * Loads an X509 RSA public key from base64.
     */
    private fun loadRsa(base64: String): RSAPublicKey {
        val bytes = Base64.getDecoder().decode(base64)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePublic(X509EncodedKeySpec(bytes)) as RSAPublicKey
    }
}
