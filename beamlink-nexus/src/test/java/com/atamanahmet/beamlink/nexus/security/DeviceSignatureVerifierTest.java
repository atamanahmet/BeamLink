package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceSignatureVerifierTest {

    private static KeyPair pair;
    private static String publicB64;
    private static String deviceId;
    private static String challenge;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        pair = gen.generateKeyPair();
        publicB64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        deviceId = UUID.randomUUID().toString();
        challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    }

    @Test
    void validSignaturePasses() throws Exception {
        String sig = sign(pair, deviceId, challenge);
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64, deviceId, challenge, sig, TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF))
                .isTrue();
    }

    @Test
    void wrongKeyFails() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair other = gen.generateKeyPair();
        String sig = sign(other, deviceId, challenge);
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64, deviceId, challenge, sig, TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF))
                .isFalse();
    }

    @Test
    void tamperedDeviceIdFails() throws Exception {
        String sig = sign(pair, deviceId, challenge);
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64,
                        UUID.randomUUID().toString(),
                        challenge,
                        sig,
                        TLS_FINGERPRINT,
                        TLS_PORT,
                        NEXUS_LEAF))
                .isFalse();
    }

    @Test
    void tamperedChallengeFails() throws Exception {
        String sig = sign(pair, deviceId, challenge);
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64, deviceId, challenge + "x", sig, TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF))
                .isFalse();
    }

    @Test
    void malformedBase64Fails() {
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64, deviceId, challenge, "%%%", TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF))
                .isFalse();
        assertThat(DeviceSignatureVerifier.verify(
                        "not-base64", deviceId, challenge, "aa", TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF))
                .isFalse();
    }

    @Test
    void fingerprintKnownVector() throws Exception {
        byte[] spki = pair.getPublic().getEncoded();
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = sha.digest(spki);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        String expected = hex.substring(0, 32).replaceAll("(.{4})", "$1 ").trim();
        assertThat(DeviceSignatureVerifier.fingerprint(spki)).isEqualTo(expected);
    }

    private static String sign(KeyPair keys, String id, String ch) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keys.getPrivate());
        signer.update(DeviceSignatureVerifier.signedMessage(id, ch, TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    @Test
    void v1SignatureFailsV2Verify() throws Exception {
        String v1 = "beamlink-token-v1\n" + deviceId + "\n" + challenge
                + "\n" + TLS_FINGERPRINT + "\n" + TLS_PORT;
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(v1.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sig = Base64.getEncoder().encodeToString(signer.sign());
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64, deviceId, challenge, sig, TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF))
                .isFalse();
    }

    @Test
    void wrongNexusLeafFails() throws Exception {
        String sig = sign(pair, deviceId, challenge);
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64,
                        deviceId,
                        challenge,
                        sig,
                        TLS_FINGERPRINT,
                        TLS_PORT,
                        "ffff eeee dddd cccc bbbb aaaa 1111 0000"))
                .isFalse();
    }

    @Test
    void emptyNexusLeafFailsWhenSignedWithLeaf() throws Exception {
        String sig = sign(pair, deviceId, challenge);
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64, deviceId, challenge, sig, TLS_FINGERPRINT, TLS_PORT, ""))
                .isFalse();
    }

    /**
     * A token signed with the spaced display leaf fails verify.
     */
    @Test
    void spacedDisplayLeafSignatureFailsCanonicalVerify() throws Exception {
        String display = "bbbb cccc dddd eeee ffff 0000 1111 2222";
        String canonical = "bbbbccccddddeeeeffff000011112222";
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(DeviceSignatureVerifier.signedMessage(
                deviceId, challenge, TLS_FINGERPRINT, TLS_PORT, display));
        String sig = Base64.getEncoder().encodeToString(signer.sign());
        assertThat(DeviceSignatureVerifier.verify(
                        publicB64, deviceId, challenge, sig, TLS_FINGERPRINT, TLS_PORT, canonical))
                .isFalse();
    }

    private static final String TLS_FINGERPRINT = "aaaa bbbb cccc dddd eeee ffff 0000 1111";
    private static final Integer TLS_PORT = 9091;
    private static final String NEXUS_LEAF = "bbbb cccc dddd eeee ffff 0000 1111 2222";
}
