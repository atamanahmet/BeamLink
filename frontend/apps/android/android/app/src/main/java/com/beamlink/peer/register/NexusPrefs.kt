package com.beamlink.peer.register

import android.annotation.SuppressLint
import android.content.Context
import com.beamlink.peer.receive.DiskReserve
import java.util.LinkedHashMap

/**
 * Non-secret nexus and registration prefs. Tokens stay in TokenStore.
 */
class NexusPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var nexusUrl: String
        get() = prefs.getString(KEY_NEXUS_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_NEXUS_URL, value).apply()
        }

    var tlsPin: String
        get() = prefs.getString(KEY_TLS_PIN, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_TLS_PIN, value).apply()
        }

    var registrationState: String
        get() = prefs.getString(KEY_STATE, STATE_NOT_CONFIGURED) ?: STATE_NOT_CONFIGURED
        set(value) {
            prefs.edit().putString(KEY_STATE, value).apply()
        }

    var lastError: String
        get() = prefs.getString(KEY_ERROR, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_ERROR, value).apply()
        }

    var registeredIp: String
        get() = prefs.getString(KEY_IP, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_IP, value).apply()
        }

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        }

    /**
     * Stores a receive session blob for restart.
     */
    fun saveReceive(transferId: String, json: String) {
        prefs.edit().putString(KEY_RECV_PREFIX + transferId, json).apply()
    }

    /**
     * Loads every stored receive blob.
     */
    fun loadReceives(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((key, value) in prefs.all) {
            if (key.startsWith(KEY_RECV_PREFIX) && value is String) {
                out[key.removePrefix(KEY_RECV_PREFIX)] = value
            }
        }
        return out
    }

    /**
     * Drops one stored receive blob.
     */
    fun clearReceive(transferId: String) {
        prefs.edit().remove(KEY_RECV_PREFIX + transferId).apply()
    }

    /**
     * Wipes all prefs to first-run defaults.
     */
    @SuppressLint("ApplySharedPref")
    fun clear() {
        prefs.edit().clear().commit()
    }

    /**
     * Receive toggle. Default off.
     */
    var receiveEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECEIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_RECEIVE, value).apply()
        }

    var minFreeSpaceBytes: Long
        get() {
            val stored = prefs.getLong(KEY_MIN_FREE, DiskReserve.DEFAULT_BYTES)
            return if (stored == 0L) DiskReserve.DEFAULT_BYTES else stored
        }
        set(value) {
            prefs.edit().putLong(KEY_MIN_FREE, DiskReserve.validate(value)).apply()
        }

    companion object {
        const val STATE_NOT_CONFIGURED = "not_configured"
        const val STATE_REGISTERING = "registering"
        const val STATE_PENDING = "PENDING"
        const val STATE_APPROVED = "APPROVED"
        const val STATE_REJECTED = "REJECTED"
        const val STATE_ERROR = "error"

        private const val PREFS = "beamlink_nexus_prefs"
        private const val KEY_NEXUS_URL = "nexus_url"
        private const val KEY_TLS_PIN = "nexus_tls_pin"
        private const val KEY_STATE = "registration_state"
        private const val KEY_ERROR = "last_error"
        private const val KEY_IP = "registered_ip"
        private const val KEY_DEVICE_ID = "peer_id"
        private const val KEY_RECEIVE = "receive_enabled"
        private const val KEY_MIN_FREE = "min_free_space_bytes"
        private const val KEY_RECV_PREFIX = "recv_"
    }
}
