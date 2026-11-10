package com.beamlink.peer.security

import android.util.Log
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager

/**
 * TLS key manager that only offers the phone receive alias.
 */
class PhoneTlsKeyManager(
    private val phoneTlsCert: PhoneTlsCert,
) : X509KeyManager {

    /**
     * Returns null. This manager is server only.
     */
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? {
        return null
    }

    /**
     * Returns null. This manager is server only.
     */
    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? {
        return null
    }

    /**
     * Lists the phone TLS alias when the peer asks for EC.
     */
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? {
        if (keyType == KEY_TYPE_EC) {
            return arrayOf(PhoneTlsCert.ALIAS)
        }
        Log.w(TAG, "Unsupported server key type: $keyType")
        return null
    }

    /**
     * Picks the phone TLS alias when the peer asks for EC.
     */
    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? {
        if (keyType == KEY_TYPE_EC) {
            return PhoneTlsCert.ALIAS
        }
        Log.w(TAG, "Unsupported server key type: $keyType")
        return null
    }

    /**
     * Returns the phone TLS cert chain for the known alias.
     */
    override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
        if (alias != PhoneTlsCert.ALIAS) {
            return null
        }
        return phoneTlsCert.certificateChain()
    }

    /**
     * Returns the phone TLS private key for the known alias.
     */
    override fun getPrivateKey(alias: String?): PrivateKey? {
        if (alias != PhoneTlsCert.ALIAS) {
            return null
        }
        return phoneTlsCert.privateKey()
    }

    companion object {
        private const val TAG = "PhoneTlsKeyManager"
        private const val KEY_TYPE_EC = "EC"
    }
}
