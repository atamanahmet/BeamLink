package com.beamlink.peer.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * EC P-256 device key in Android Keystore. Non exportable. Sign only.
 */
class DeviceKey(@Suppress("UNUSED_PARAMETER") context: Context) {

    init {
        loadOrCreate()
    }

    /**
     * Standard-base64 X.509 SPKI public key.
     */
    fun publicKeyBase64(): String {
        val pub = entry().certificate.publicKey
        return Base64.getEncoder().encodeToString(pub.encoded)
    }

    /**
     * SHA-256 of SPKI, hex, first 32 chars, groups of 4.
     */
    fun fingerprint(): String {
        return fingerprint(entry().certificate.publicKey.encoded)
    }

    /**
     * Deletes the Keystore key. Next sign or fingerprint creates a new pair.
     */
    fun delete() {
        val ks = KeyStore.getInstance(ANDROID)
        ks.load(null)
        if (ks.containsAlias(ALIAS)) {
            ks.deleteEntry(ALIAS)
        }
    }

    /**
     * DER signature in standard base64 over UTF-8 message bytes.
     */
    fun sign(message: ByteArray): String {
        val sig = Signature.getInstance(SIG_ALG)
        sig.initSign(entry().privateKey)
        sig.update(message)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun loadOrCreate() {
        val ks = KeyStore.getInstance(ANDROID)
        ks.load(null)
        if (ks.containsAlias(ALIAS)) {
            return
        }
        generate(strongBox = true)
    }

    private fun generate(strongBox: Boolean) {
        try {
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID)
            val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true)
            }
            gen.initialize(builder.build())
            gen.generateKeyPair()
        } catch (e: Exception) {
            if (strongBox) {
                generate(strongBox = false)
                return
            }
            throw IllegalStateException("Device key create failed", e)
        }
    }

    private fun entry(): KeyStore.PrivateKeyEntry {
        loadOrCreate()
        val ks = KeyStore.getInstance(ANDROID)
        ks.load(null)
        val item = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Device key missing from Keystore")
        return item
    }

    companion object {
        private const val ANDROID = "AndroidKeyStore"
        private const val ALIAS = "beamlink_device_ec"
        private const val CURVE = "secp256r1"
        private const val SIG_ALG = "SHA256withECDSA"

        /**
         * SHA-256 of the bytes, first 32 hex, lowercase, no spaces.
         */
        fun canonicalFingerprint(der: ByteArray): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(der)
            val hex = hash.joinToString("") { b -> "%02x".format(b) }
            return hex.substring(0, 32)
        }

        /**
         * Display form of the canonical fingerprint, groups of 4.
         */
        fun fingerprint(spki: ByteArray): String {
            return canonicalFingerprint(spki).chunked(4).joinToString(" ")
        }
    }
}
