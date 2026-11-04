package com.beamlink.peer.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Phone receive TLS leaf in Android Keystore. EC P-256, non extractable, reused.
 */
class PhoneTlsCert {

    /**
     * Creates the key and self-signed cert once, then reuses the alias.
     */
    fun loadOrCreate() {
        val ks = store()
        if (ks.containsAlias(ALIAS)) {
            return
        }
        val now = Date()
        val notAfter = Date(now.time + TEN_YEARS_MS)
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setCertificateSubject(X500Principal(SUBJECT))
            .setCertificateNotBefore(now)
            .setCertificateNotAfter(notAfter)
            .build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID)
        gen.initialize(spec)
        gen.generateKeyPair()
    }

    /**
     * Deletes the TLS key and cert from Keystore.
     */
    fun delete() {
        val ks = store()
        if (ks.containsAlias(ALIAS)) {
            ks.deleteEntry(ALIAS)
        }
    }

    /**
     * Self-signed leaf from the Keystore alias.
     */
    fun leaf(): X509Certificate {
        return store().getCertificate(ALIAS) as? X509Certificate
            ?: throw IllegalStateException("Phone TLS cert missing")
    }

    /**
     * Leaf fingerprint using the shared pin helper.
     */
    fun fingerprint(): String {
        return NexusTlsFingerprint.of(leaf())
    }

    /**
     * AndroidKeyStore instance that holds the TLS key.
     */
    fun keyStore(): KeyStore {
        return store()
    }

    private fun store(): KeyStore {
        val ks = KeyStore.getInstance(ANDROID)
        ks.load(null)
        return ks
    }

    companion object {
        const val ALIAS = "beamlink_phone_tls"
        private const val ANDROID = "AndroidKeyStore"
        private const val CURVE = "secp256r1"
        private const val SUBJECT = "CN=beamlink-peer"
        private const val TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000
    }
}
