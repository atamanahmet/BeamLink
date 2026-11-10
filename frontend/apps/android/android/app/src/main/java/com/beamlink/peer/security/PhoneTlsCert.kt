package com.beamlink.peer.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
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
     * Recreates the key when the stored digests are wrong.
     */
    @Synchronized
    fun loadOrCreate() {
        val ks = store()
        if (ks.containsAlias(ALIAS)) {
            if (hasRequiredDigests(ks)) {
                return
            }
            Log.w(TAG, "Phone TLS key digests wrong, deleting and creating again")
            ks.deleteEntry(ALIAS)
        }
        val now = Date()
        val notAfter = Date(now.time + TEN_YEARS_MS)
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
            .setDigests(*REQUIRED_DIGESTS)
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
     * Private key handle for the phone TLS alias.
     */
    fun privateKey(): PrivateKey {
        return entry().privateKey
    }

    /**
     * Cert chain for the phone TLS alias.
     */
    fun certificateChain(): Array<X509Certificate> {
        return entry().certificateChain.map { it as X509Certificate }.toTypedArray()
    }

    private fun entry(): KeyStore.PrivateKeyEntry {
        val item = store().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Phone TLS key missing")
        return item
    }

    /**
     * True when the stored key allows every required digest.
     */
    private fun hasRequiredDigests(ks: KeyStore): Boolean {
        val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return false
        return try {
            val factory = KeyFactory.getInstance(entry.privateKey.algorithm, ANDROID)
            val info = factory.getKeySpec(entry.privateKey, KeyInfo::class.java)
            REQUIRED_DIGESTS.all { digest -> info.digests.contains(digest) }
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    private fun store(): KeyStore {
        val ks = KeyStore.getInstance(ANDROID)
        ks.load(null)
        return ks
    }

    companion object {
        const val ALIAS = "beamlink_phone_tls"
        private const val TAG = "PhoneTlsCert"
        private const val ANDROID = "AndroidKeyStore"
        private const val CURVE = "secp256r1"
        private const val SUBJECT = "CN=beamlink-peer"
        private const val TEN_YEARS_MS = 10L * 365 * 24 * 60 * 60 * 1000
        private val REQUIRED_DIGESTS = arrayOf(
            KeyProperties.DIGEST_SHA256,
            KeyProperties.DIGEST_NONE,
        )
    }
}
