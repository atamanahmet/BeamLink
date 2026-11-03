package com.atamanahmet.beamlink.peer.security;

/**
 * Canonical strings the device signs for nexus device enrollment.
 */
public final class DeviceSignaturePayload {

    public static final String TOKEN_PREFIX = "beamlink-token-v2";
    public static final String REGISTER_PREFIX = "beamlink-register-v1";

    private DeviceSignaturePayload() {
    }

    /**
     * Canonical token string with TLS bind lines and nexus leaf. Missing TLS is empty.
     */
    public static String signedMessage(
            String deviceId,
            String challenge,
            String tlsFingerprint,
            Integer tlsPort,
            String nexusLeafSha256) {
        return TOKEN_PREFIX + "\n" + deviceId + "\n" + challenge
                + "\n" + missing(tlsFingerprint) + "\n" + missingPort(tlsPort)
                + "\n" + missing(nexusLeafSha256);
    }

    /**
     * Canonical register string. Missing TLS is empty.
     */
    public static String signedRegisterMessage(
            String publicKey, String tlsFingerprint, Integer tlsPort) {
        return REGISTER_PREFIX + "\n" + publicKey
                + "\n" + missing(tlsFingerprint) + "\n" + missingPort(tlsPort);
    }

    /**
     * Canonical missing JSON null becomes an empty line.
     */
    static String missing(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    /**
     * Canonical missing JSON null port becomes an empty line.
     */
    static String missingPort(Integer port) {
        return port == null ? "" : Integer.toString(port);
    }
}
