package com.atamanahmet.beamlink.nexus.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceSignatureVerifierCanonicalTest {

    private static final String PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETEST";
    private static final String DEVICE_ID = "11111111-2222-4333-8444-555555555555";
    private static final String CHALLENGE = "challenge-fixed-value";
    private static final String TLS_FINGERPRINT = "aaaa bbbb cccc dddd eeee ffff 0000 1111";
    private static final Integer TLS_PORT = 9091;
    private static final String NEXUS_LEAF = "bbbbccccddddeeeeffff000011112222";

    @Test
    void registerListed() {
        assertThat(DeviceSignatureVerifier.signedRegisterMessageText(
                PUBLIC_KEY, TLS_FINGERPRINT, TLS_PORT))
                .isEqualTo("beamlink-register-v1\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETEST\naaaa bbbb cccc dddd eeee ffff 0000 1111\n9091");
    }

    @Test
    void registerMissingTls() {
        assertThat(DeviceSignatureVerifier.signedRegisterMessageText(PUBLIC_KEY, null, null))
                .isEqualTo("beamlink-register-v1\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETEST\n\n");
    }

    /**
     * Token vector must match the other two sides.
     */
    @Test
    void tokenListed() {
        assertThat(DeviceSignatureVerifier.signedMessageText(
                DEVICE_ID, CHALLENGE, TLS_FINGERPRINT, TLS_PORT, NEXUS_LEAF))
                .isEqualTo("beamlink-token-v2\n11111111-2222-4333-8444-555555555555\nchallenge-fixed-value\naaaa bbbb cccc dddd eeee ffff 0000 1111\n9091\nbbbbccccddddeeeeffff000011112222");
    }

    @Test
    void tokenMissingTls() {
        assertThat(DeviceSignatureVerifier.signedMessageText(DEVICE_ID, CHALLENGE, null, null, NEXUS_LEAF))
                .isEqualTo("beamlink-token-v2\n11111111-2222-4333-8444-555555555555\nchallenge-fixed-value\n\n\nbbbbccccddddeeeeffff000011112222");
    }

    @Test
    void tokenEmptyNexusLeaf() {
        assertThat(DeviceSignatureVerifier.signedMessageText(DEVICE_ID, CHALLENGE, TLS_FINGERPRINT, TLS_PORT, null))
                .isEqualTo("beamlink-token-v2\n11111111-2222-4333-8444-555555555555\nchallenge-fixed-value\naaaa bbbb cccc dddd eeee ffff 0000 1111\n9091\n");
    }
}
