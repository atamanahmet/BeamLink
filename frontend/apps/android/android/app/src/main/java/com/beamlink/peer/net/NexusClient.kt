package com.beamlink.peer.net

import com.beamlink.peer.config.DeviceDefaults
import org.json.JSONObject

/**
 * Nexus identify and peer summary calls.
 */
class NexusClient(
    private val http: HttpJsonClient = HttpJsonClient(),
) {

    data class IdentifyResult(
        val deviceId: String,
        val deviceName: String,
        val state: String,
        val publicId: String?,
        val nexusPublicKey: String? = null,
        val notFound: Boolean,
    )

    data class PeerSummary(
        val id: String,
        val deviceName: String,
        val ipAddress: String,
        val port: Int,
        val online: Boolean,
        val tlsPort: Int? = null,
        val tlsFingerprint: String? = null,
    )

    /**
     * GET /api/devices/identify?ipAddress=&port=. No nonce. 404 => notFound.
     */
    fun identify(nexusBaseUrl: String, ipAddress: String, port: Int): IdentifyResult {
        val base = AddressGuard.requireAllowedNexusUrl(nexusBaseUrl).toString().trimEnd('/')
        val url = "$base/api/devices/identify?ipAddress=${encode(ipAddress)}&port=$port"
        val response = http.get(
            url,
            timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
        )
        if (response.status == 404) {
            return IdentifyResult("", "", "", null, null, notFound = true)
        }
        if (response.status !in 200..299) {
            throw IllegalStateException(ProblemBody.userText(response.body, response.status))
        }
        val json = HttpJsonClient.parseObject(response.body)
        return IdentifyResult(
            deviceId = json.getString("deviceId"),
            deviceName = json.optString("deviceName", ""),
            state = json.optString("state", ""),
            publicId = stringOrNull(json, "publicId"),
            nexusPublicKey = stringOrNull(json, "nexusPublicKey"),
            notFound = false,
        )
    }

    private fun stringOrNull(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optString(key, "")
        return value.takeIf { it.isNotBlank() }
    }

    /**
     * GET /api/nexus/peers with X-Auth-Token public token. TLS fields on APPROVED peers.
     */
    fun fetchPeerSummaries(nexusBaseUrl: String, publicToken: String): List<PeerSummary> {
        val base = AddressGuard.requireAllowedNexusUrl(nexusBaseUrl).toString().trimEnd('/')
        val response = http.get(
            "$base/api/nexus/peers",
            headers = mapOf("X-Auth-Token" to publicToken),
            timeoutMs = (DeviceDefaults.REQUEST_TIMEOUT_SECONDS * 1000).toInt(),
        )
        if (response.status !in 200..299) {
            throw IllegalStateException(ProblemBody.userText(response.body, response.status))
        }
        val root = HttpJsonClient.parseObject(response.body)
        val array = root.getJSONArray("peers")
        val out = ArrayList<PeerSummary>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            out.add(
                PeerSummary(
                    id = item.getString("id"),
                    deviceName = item.optString("deviceName", ""),
                    ipAddress = item.optString("ipAddress", ""),
                    port = item.optInt("port", 0),
                    online = item.optBoolean("online", false),
                    tlsPort = intOrNull(item, "tlsPort"),
                    tlsFingerprint = stringOrNull(item, "tlsFingerprint"),
                ),
            )
        }
        return out
    }

    private fun intOrNull(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optInt(key, 0)
        return value.takeIf { it > 0 }
    }

    private fun encode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }

}
