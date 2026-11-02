package com.atamanahmet.beamlink.nexus.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * ECDSA P-256 verify and fingerprint for device public keys.
 */
public final class DeviceSignatureVerifier {

    public static final String SIGNED_PREFIX = "beamlink-token-v2";
    public static final String REGISTER_PREFIX = "beamlink-register-v1";
    private static final String EC = "EC";
    private static final String SIG_ALG = "SHA256withECDSA";

    private DeviceSignatureVerifier() {
    }

    /**
     * Canonical token string with TLS bind lines and nexus leaf. Missing TLS is empty.
     */
    public static String signedMessageText(
            String deviceId,
            String challenge,
            String tlsFingerprint,
            Integer tlsPort,
            String nexusLeafSha256) {
        return SIGNED_PREFIX + "\n" + deviceId + "\n" + challenge
                + "\n" + missing(tlsFingerprint) + "\n" + missingPort(tlsPort)
                + "\n" + missing(nexusLeafSha256);
    }

    /**
     * Token message with TLS bind lines and nexus leaf. Missing TLS is the empty string.
     */
    public static byte[] signedMessage(
            String deviceId,
            String challenge,
            String tlsFingerprint,
            Integer tlsPort,
            String nexusLeafSha256) {
        return signedMessageText(deviceId, challenge, tlsFingerprint, tlsPort, nexusLeafSha256)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Canonical register string. Missing TLS is empty.
     */
    public static String signedRegisterMessageText(
            String publicKey, String tlsFingerprint, Integer tlsPort) {
        return REGISTER_PREFIX + "\n" + publicKey
                + "\n" + missing(tlsFingerprint) + "\n" + missingPort(tlsPort);
    }

    /**
     * Register message. Missing TLS is the empty string.
     */
    public static byte[] signedRegisterMessage(
            String publicKey, String tlsFingerprint, Integer tlsPort) {
        return signedRegisterMessageText(publicKey, tlsFingerprint, tlsPort)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * True when a TLS fingerprint is present. JSON null is the canonical missing form.
     */
    public static boolean tlsListed(String tlsFingerprint) {
        return tlsFingerprint != null && !tlsFingerprint.isBlank();
    }

    /**
     * Canonical missing JSON null becomes an empty line in the signed bytes.
     */
    public static String missing(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    /**
     * Canonical missing JSON null port becomes an empty line in the signed bytes.
     */
    public static String missingPort(Integer port) {
        return port == null ? "" : Integer.toString(port);
    }

    /**
     * Parses X.509 SPKI DER from standard base64.
     */
    public static PublicKey parsePublicKey(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new IllegalArgumentException("publicKey missing");
        }
        try {
            byte[] spki = Base64.getDecoder().decode(publicKeyBase64);
            return KeyFactory.getInstance(EC).generatePublic(new X509EncodedKeySpec(spki));
        } catch (Exception e) {
            throw new IllegalArgumentException("publicKey invalid", e);
        }
    }

    /**
     * SHA-256 of the bytes, first 32 hex, lowercase, no spaces.
     */
    public static String canonicalFingerprint(byte[] der) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(der);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("fingerprint failed", e);
        }
    }

    /**
     * Display form of the canonical fingerprint, groups of 4.
     */
    public static String fingerprint(byte[] spki) {
        String first = canonicalFingerprint(spki);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < first.length(); i += 4) {
            if (i > 0) {
                grouped.append(' ');
            }
            grouped.append(first, i, i + 4);
        }
        return grouped.toString();
    }

    /**
     * Fingerprint from a standard-base64 SPKI public key.
     */
    public static String fingerprintFromPublicKey(String publicKeyBase64) {
        byte[] spki = Base64.getDecoder().decode(publicKeyBase64);
        return fingerprint(spki);
    }

    /**
     * True when signature is valid over the token TLS bind message.
     */
    public static boolean verify(
            String publicKeyBase64,
            String deviceId,
            String challenge,
            String signatureBase64,
            String tlsFingerprint,
            Integer tlsPort,
            String nexusLeafSha256) {
        if (publicKeyBase64 == null || deviceId == null || challenge == null || signatureBase64 == null) {
            return false;
        }
        try {
            PublicKey publicKey = parsePublicKey(publicKeyBase64);
            byte[] sig = Base64.getDecoder().decode(signatureBase64);
            Signature verifier = Signature.getInstance(SIG_ALG);
            verifier.initVerify(publicKey);
            verifier.update(signedMessage(deviceId, challenge, tlsFingerprint, tlsPort, nexusLeafSha256));
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * True when signature is valid over the register TLS bind message.
     */
    public static boolean verifyRegister(
            String publicKeyBase64,
            String tlsFingerprint,
            Integer tlsPort,
            String signatureBase64) {
        if (publicKeyBase64 == null || signatureBase64 == null) {
            return false;
        }
        try {
            PublicKey publicKey = parsePublicKey(publicKeyBase64);
            byte[] sig = Base64.getDecoder().decode(signatureBase64);
            Signature verifier = Signature.getInstance(SIG_ALG);
            verifier.initVerify(publicKey);
            verifier.update(signedRegisterMessage(publicKeyBase64, tlsFingerprint, tlsPort));
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }
}
