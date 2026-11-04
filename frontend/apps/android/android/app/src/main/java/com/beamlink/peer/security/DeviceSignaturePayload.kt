package com.beamlink.peer.security

/**
 * Canonical strings the phone signs for nexus device enrollment.
 */
object DeviceSignaturePayload {

    const val TOKEN_PREFIX = "beamlink-token-v2"
    const val REGISTER_PREFIX = "beamlink-register-v1"

    /**
     * Canonical token string with TLS bind lines and nexus leaf. Missing TLS is empty.
     */
    fun signedMessage(
        deviceId: String,
        challenge: String,
        tlsFingerprint: String?,
        tlsPort: Int?,
        nexusLeafSha256: String?,
    ): String {
        return "$TOKEN_PREFIX\n$deviceId\n$challenge\n${missing(tlsFingerprint)}\n${missingPort(tlsPort)}\n${missing(nexusLeafSha256)}"
    }

    /**
     * Canonical register string. Missing TLS is empty.
     */
    fun signedRegisterMessage(
        publicKey: String,
        tlsFingerprint: String?,
        tlsPort: Int?,
    ): String {
        return "$REGISTER_PREFIX\n$publicKey\n${missing(tlsFingerprint)}\n${missingPort(tlsPort)}"
    }

    /**
     * Canonical missing JSON null becomes an empty line.
     */
    fun missing(value: String?): String {
        return if (value.isNullOrBlank()) "" else value
    }

    /**
     * Canonical missing JSON null port becomes an empty line.
     */
    fun missingPort(port: Int?): String {
        return port?.toString() ?: ""
    }
}
