package com.atamanahmet.beamlink.peer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PeerTokenVerifierTest {

    private PeerPublicTokenTestHelper keys;
    private PeerTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        keys = new PeerPublicTokenTestHelper();
        verifier = new PeerTokenVerifier();
    }

    @Test
    void validTokenReturnsPeerPrincipal() {
        UUID deviceId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String token = keys.sign(deviceId, publicId);

        Optional<PeerPrincipal> result = verifier.verify(token, publicId, keys.nexusPublicKeyB64);

        assertThat(result).isPresent();
        assertThat(result.get().publicId()).isEqualTo(publicId.toString());
        assertThat(result.get().deviceId()).isEqualTo(deviceId);
    }

    @Test
    void tokenWithoutSubIsRejected() {
        UUID publicId = UUID.randomUUID();
        String token = keys.signWithRawSubject(null, publicId);

        assertThat(verifier.verify(token, publicId, keys.nexusPublicKeyB64)).isEmpty();
    }

    @Test
    void tokenWithNonUuidSubIsRejected() {
        UUID publicId = UUID.randomUUID();
        String token = keys.signWithRawSubject("not-a-uuid", publicId);

        assertThat(verifier.verify(token, publicId, keys.nexusPublicKeyB64)).isEmpty();
    }

    @Test
    void publicIdHeaderMismatchIsRejected() {
        UUID deviceId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String token = keys.sign(deviceId, publicId);

        assertThat(verifier.verify(token, UUID.randomUUID(), keys.nexusPublicKeyB64)).isEmpty();
    }
}
