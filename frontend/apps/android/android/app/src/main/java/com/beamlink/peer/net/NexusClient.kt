package com.beamlink.peer.net

import com.beamlink.peer.config.DeviceDefaults
import org.json.JSONObject

/**
 * Nexus peer summary calls.
 */
class NexusClient(
    private val http: HttpJsonClient = HttpJsonClient(),
) {

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

    private fun stringOrNull(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optString(key, "")
        return value.takeIf { it.isNotBlank() }
    }

    private fun intOrNull(json: JSONObject, key: String): Int? {
        if (!json.has(key) || json.isNull(key)) return null
        val value = json.optInt(key, 0)
        return value.takeIf { it > 0 }
    }

}
