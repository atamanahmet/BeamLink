package com.beamlink.peer.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores device tokens encrypted with an Android Keystore AES key.
 * Ciphertext lives in a private app file, not SharedPreferences.
 */
class TokenStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)

    data class Tokens(
        val deviceId: String,
        val publicId: String,
        val authToken: String,
        val publicToken: String,
        val deviceName: String,
        val nexusPublicKey: String? = null,
    )

    /**
     * Writes tokens under Keystore AES-GCM.
     */
    fun save(tokens: Tokens) {
        val json = JSONObject()
            .put("deviceId", tokens.deviceId)
            .put("publicId", tokens.publicId)
            .put("authToken", tokens.authToken)
            .put("publicToken", tokens.publicToken)
            .put("deviceName", tokens.deviceName)
            .put("nexusPublicKey", tokens.nexusPublicKey ?: "")
            .toString()
            .toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(json)
        val out = ByteArray(4 + iv.size + encrypted.size)
        out[0] = ((iv.size ushr 24) and 0xff).toByte()
        out[1] = ((iv.size ushr 16) and 0xff).toByte()
        out[2] = ((iv.size ushr 8) and 0xff).toByte()
        out[3] = (iv.size and 0xff).toByte()
        System.arraycopy(iv, 0, out, 4, iv.size)
        System.arraycopy(encrypted, 0, out, 4 + iv.size, encrypted.size)
        file.writeBytes(out)
    }

    /**
     * Reads tokens or null when missing or corrupt. Failed decrypt clears the file.
     */
    fun load(): Tokens? {
        if (!file.exists()) return null
        return try {
            val raw = file.readBytes()
            if (raw.size < 5) {
                clear()
                return null
            }
            val ivSize = ((raw[0].toInt() and 0xff) shl 24) or
                ((raw[1].toInt() and 0xff) shl 16) or
                ((raw[2].toInt() and 0xff) shl 8) or
                (raw[3].toInt() and 0xff)
            if (ivSize <= 0 || 4 + ivSize >= raw.size) {
                clear()
                return null
            }
            val iv = raw.copyOfRange(4, 4 + ivSize)
            val encrypted = raw.copyOfRange(4 + ivSize, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
            Tokens(
                deviceId = json.getString("deviceId"),
                publicId = json.getString("publicId"),
                authToken = json.getString("authToken"),
                publicToken = json.getString("publicToken"),
                deviceName = json.optString("deviceName", ""),
                nexusPublicKey = json.optString("nexusPublicKey", "").takeIf { it.isNotBlank() },
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    /**
     * Deletes the encrypted token file.
     */
    fun clear() {
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "beamlink_agent_tokens"
        private const val FILE_NAME = "peer_tokens.enc"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
