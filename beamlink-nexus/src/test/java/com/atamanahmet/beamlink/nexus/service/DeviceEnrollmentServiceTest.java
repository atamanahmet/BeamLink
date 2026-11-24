package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.RsaKeyConfig;
import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.dto.DeviceRegistrationRequest;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import com.atamanahmet.beamlink.nexus.security.DeviceSignatureVerifier;
import com.atamanahmet.beamlink.nexus.security.NexusTlsCert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceEnrollmentServiceTest {

    private static final byte[] LEAF_DER = new byte[]{1, 2, 3, 4};
    private static final String WRONG_LEAF =
            "ffffeeeebbbbccccddddeeeeaaaa1111000022223333444455556666777788889999";
    private static final String DEVICE_TLS =
            "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999";
    private static KeyPair pair;
    private static String publicB64;
    private static String ownLeaf;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        pair = gen.generateKeyPair();
        publicB64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(LEAF_DER);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        ownLeaf = hex.toString();
    }

    @Test
    void registerRejectsTamperedTlsFingerprint() throws Exception {
        DeviceService devices = mock(DeviceService.class);
        when(devices.findByFingerprint(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        DeviceEnrollmentService service = new DeviceEnrollmentService(
                devices,
                mock(ChallengeStore.class),
                mock(DeviceTokenService.class),
                mock(RsaKeyConfig.class),
                mock(NexusTlsCert.class));

        String honest = DEVICE_TLS;
        String tampered = WRONG_LEAF;
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(DeviceSignatureVerifier.signedRegisterMessage(publicB64, honest, 9091));
        String sig = Base64.getEncoder().encodeToString(signer.sign());

        DeviceRegistrationRequest request = new DeviceRegistrationRequest();
        request.setDeviceName("device-a");
        request.setIpAddress("10.0.0.8");
        request.setPort(9090);
        request.setPublicKey(publicB64);
        request.setTlsFingerprint(tampered);
        request.setTlsPort(9091);
        request.setSignature(sig);

        assertThatThrownBy(() -> service.register(request, "10.0.0.8"))
                .isInstanceOf(DeviceEnrollmentService.EnrollmentDeniedException.class)
                .extracting(ex -> ((DeviceEnrollmentService.EnrollmentDeniedException) ex).getError())
                .isEqualTo("invalid_grant");
    }

    @Test
    void tokenRejectsTamperedTlsFingerprintOrPort() throws Exception {
        TokenSetup setup = approvedSetup();
        String honestFp = DEVICE_TLS;
        String tamperedFp = WRONG_LEAF;
        String sig = signToken(setup.deviceId.toString(), setup.challenge, honestFp, 9091, ownLeaf);

        DeviceEnrollmentService.TokenPollResult result = setup.service.token(
                setup.deviceId, setup.challenge, sig, tamperedFp, 9092, ownLeaf, setup.now);

        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.error()).isEqualTo("invalid_grant");
        assertThat(result.body()).isNull();
        assertThat(setup.device.getTlsFingerprint()).isEqualTo(honestFp);
        assertThat(setup.device.getTlsPort()).isEqualTo(9091);
        verify(setup.devices, never()).saveDevice(any());
    }

    @Test
    void tokenAcceptsSignedTlsChangeOnApprovedDevice() throws Exception {
        TokenSetup setup = approvedSetup();
        DeviceTokenService tokens = mock(DeviceTokenService.class);
        when(tokens.generateAuthToken(setup.deviceId)).thenReturn("auth");
        when(tokens.generatePublicToken(setup.deviceId, setup.publicId)).thenReturn("pub");
        RsaKeyConfig rsa = mock(RsaKeyConfig.class);
        when(rsa.getPublicKeyBase64()).thenReturn("nexus-pk");
        DeviceEnrollmentService service = new DeviceEnrollmentService(
                setup.devices, setup.store, tokens, rsa, tlsCert());

        String nextFp =
                "111122223333444455556666777788889999aaaabbbbccccddddeeeeffff00001111";
        int nextPort = 9092;
        String sig = signToken(setup.deviceId.toString(), setup.challenge, nextFp, nextPort, ownLeaf);

        DeviceEnrollmentService.TokenPollResult result = service.token(
                setup.deviceId, setup.challenge, sig, nextFp, nextPort, ownLeaf, setup.now);

        assertThat(result.status()).isEqualTo(HttpStatus.OK);
        assertThat(result.body()).isNotNull();
        assertThat(setup.device.getTlsFingerprint()).isEqualTo(nextFp);
        assertThat(setup.device.getTlsPort()).isEqualTo(nextPort);
        verify(setup.devices).saveDevice(setup.device);
    }

    @Test
    void tokenRejectsMissingNexusLeaf() throws Exception {
        TokenSetup setup = approvedSetup();
        String sig = signToken(
                setup.deviceId.toString(),
                setup.challenge,
                setup.device.getTlsFingerprint(),
                setup.device.getTlsPort(),
                ownLeaf);
        DeviceEnrollmentService.TokenPollResult result = setup.service.token(
                setup.deviceId,
                setup.challenge,
                sig,
                setup.device.getTlsFingerprint(),
                setup.device.getTlsPort(),
                null,
                setup.now);
        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.error()).isEqualTo("invalid_grant");
    }

    @Test
    void tokenRejectsEmptyNexusLeaf() throws Exception {
        TokenSetup setup = approvedSetup();
        String sig = signToken(
                setup.deviceId.toString(),
                setup.challenge,
                setup.device.getTlsFingerprint(),
                setup.device.getTlsPort(),
                "");
        DeviceEnrollmentService.TokenPollResult result = setup.service.token(
                setup.deviceId,
                setup.challenge,
                sig,
                setup.device.getTlsFingerprint(),
                setup.device.getTlsPort(),
                "",
                setup.now);
        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.error()).isEqualTo("invalid_grant");
    }

    @Test
    void tokenRejectsWrongNexusLeaf() throws Exception {
        TokenSetup setup = approvedSetup();
        String sig = signToken(
                setup.deviceId.toString(),
                setup.challenge,
                setup.device.getTlsFingerprint(),
                setup.device.getTlsPort(),
                WRONG_LEAF);
        DeviceEnrollmentService.TokenPollResult result = setup.service.token(
                setup.deviceId,
                setup.challenge,
                sig,
                setup.device.getTlsFingerprint(),
                setup.device.getTlsPort(),
                WRONG_LEAF,
                setup.now);
        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.error()).isEqualTo("invalid_grant");
    }

    @Test
    void tokenRejectsV1Signature() throws Exception {
        TokenSetup setup = approvedSetup();
        String v1 = "beamlink-token-v1\n" + setup.deviceId + "\n" + setup.challenge
                + "\n" + setup.device.getTlsFingerprint() + "\n" + setup.device.getTlsPort();
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(v1.getBytes(StandardCharsets.UTF_8));
        String sig = Base64.getEncoder().encodeToString(signer.sign());
        DeviceEnrollmentService.TokenPollResult result = setup.service.token(
                setup.deviceId,
                setup.challenge,
                sig,
                setup.device.getTlsFingerprint(),
                setup.device.getTlsPort(),
                ownLeaf,
                setup.now);
        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.error()).isEqualTo("invalid_grant");
    }

    private static TokenSetup approvedSetup() {
        UUID deviceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID publicId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Device device = approvedDevice(deviceId, publicId, DEVICE_TLS, 9091);
        DeviceService devices = mock(DeviceService.class);
        when(devices.findOptional(deviceId)).thenReturn(Optional.of(device));
        when(devices.saveDevice(any())).thenAnswer(inv -> inv.getArgument(0));
        ChallengeStore store = new ChallengeStore();
        long now = 1_000_000L;
        String challenge = store.issue(deviceId.toString(), now).orElseThrow();
        DeviceEnrollmentService service = new DeviceEnrollmentService(
                devices, store, mock(DeviceTokenService.class), mock(RsaKeyConfig.class), tlsCert());
        return new TokenSetup(deviceId, publicId, device, devices, store, service, challenge, now);
    }

    private static NexusTlsCert tlsCert() {
        try {
            X509Certificate leaf = mock(X509Certificate.class);
            when(leaf.getEncoded()).thenReturn(LEAF_DER);
            NexusTlsCert cert = mock(NexusTlsCert.class);
            when(cert.getLeaf()).thenReturn(leaf);
            return cert;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Device approvedDevice(UUID id, UUID publicId, String tlsFingerprint, int tlsPort) {
        return Device.builder()
                .id(id)
                .name("device-a")
                .ipAddress("10.0.0.8")
                .port(9090)
                .state(DeviceState.APPROVED)
                .publicId(publicId)
                .publicKey(publicB64)
                .tlsFingerprint(tlsFingerprint)
                .tlsPort(tlsPort)
                .build();
    }

    private static String signToken(
            String deviceId, String challenge, String tlsFingerprint, int tlsPort, String nexusLeaf)
            throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate());
        signer.update(DeviceSignatureVerifier.signedMessage(
                deviceId, challenge, tlsFingerprint, tlsPort, nexusLeaf));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private record TokenSetup(
            UUID deviceId,
            UUID publicId,
            Device device,
            DeviceService devices,
            ChallengeStore store,
            DeviceEnrollmentService service,
            String challenge,
            long now) {
    }
}
