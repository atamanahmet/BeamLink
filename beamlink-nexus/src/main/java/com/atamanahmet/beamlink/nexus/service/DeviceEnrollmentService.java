package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.RsaKeyConfig;
import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.dto.DeviceRegistrationRequest;
import com.atamanahmet.beamlink.nexus.dto.DeviceRegistrationResponse;
import com.atamanahmet.beamlink.nexus.dto.DeviceChallengeResponse;
import com.atamanahmet.beamlink.nexus.dto.DeviceTokenResponse;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import com.atamanahmet.beamlink.nexus.security.DeviceSignatureVerifier;
import com.atamanahmet.beamlink.nexus.security.LoopbackAddresses;
import com.atamanahmet.beamlink.nexus.security.NexusTlsCert;
import com.atamanahmet.beamlink.nexus.security.NexusTlsFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Register, challenge and token for device-key enrollment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceEnrollmentService {

    private final DeviceService deviceService;
    private final ChallengeStore challengeStore;
    private final DeviceTokenService deviceTokenService;
    private final RsaKeyConfig rsaKeyConfig;
    private final NexusTlsCert nexusTlsCert;

    private final Map<UUID, Long> lastPollMs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> intervalSec = new ConcurrentHashMap<>();

    /**
     * Stores publicKey by fingerprint. Ip and port are peer data, not identity.
     */
    @Transactional
    public DeviceRegistrationResponse register(DeviceRegistrationRequest request, String sourceIp) {
        try {
            DeviceSignatureVerifier.parsePublicKey(request.getPublicKey());
        } catch (IllegalArgumentException e) {
            throw new EnrollmentDeniedException(HttpStatus.BAD_REQUEST, "invalid_grant", e);
        }
        verifyRegisterBind(request);
        if (!LoopbackAddresses.sourceMatchesClaimed(request.getIpAddress(), sourceIp)) {
            throw new EnrollmentDeniedException(HttpStatus.FORBIDDEN, "access_denied");
        }
        String fingerprint = DeviceSignatureVerifier.fingerprintFromPublicKey(request.getPublicKey());
        String name = request.getDeviceName() != null && !request.getDeviceName().isBlank()
                ? request.getDeviceName()
                : request.getIpAddress() + ":" + request.getPort();

        Optional<Device> existing = deviceService.findByFingerprint(fingerprint);
        Device saved;
        if (existing.isPresent()) {
            Device device = existing.get();
            if (device.getState() == DeviceState.REJECTED) {
                throw new EnrollmentDeniedException(HttpStatus.FORBIDDEN, "access_denied");
            }
            device.setIpAddress(request.getIpAddress());
            device.setPort(request.getPort());
            device.setName(name);
            device.setPublicKey(request.getPublicKey());
            applyTls(device, request.getTlsFingerprint(), request.getTlsPort());
            saved = deviceService.saveDevice(device);
            log.info("Device re-register by fingerprint {}. State={}", fingerprint, saved.getState());
        } else {
            Device device = Device.builder()
                    .name(name)
                    .ipAddress(request.getIpAddress())
                    .port(request.getPort())
                    .state(DeviceState.PENDING_APPROVAL)
                    .registeredAt(Instant.now())
                    .lastSeenAt(Instant.now())
                    .publicKey(request.getPublicKey())
                    .fingerprint(fingerprint)
                    .tlsFingerprint(blankToNull(request.getTlsFingerprint()))
                    .tlsPort(request.getTlsPort())
                    .build();
            saved = deviceService.saveDevice(device);
            log.info("New device registered {} fingerprint {}", saved.getId(), fingerprint);
        }
        return new DeviceRegistrationResponse(saved.getId(), EnrollmentStateRule.DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * Issues a single-use challenge for a known device.
     */
    public DeviceChallengeResponse challenge(UUID deviceId, long nowMs) {
        Device device = deviceService.findOptional(deviceId).orElse(null);
        if (device == null) {
            throw new EnrollmentDeniedException(HttpStatus.FORBIDDEN, "access_denied");
        }
        Optional<String> issued = challengeStore.issue(deviceId.toString(), nowMs);
        if (issued.isEmpty()) {
            throw new EnrollmentDeniedException(HttpStatus.TOO_MANY_REQUESTS, "slow_down");
        }
        return new DeviceChallengeResponse(issued.get(), ChallengeStore.EXPIRES_IN_SECONDS);
    }

    /**
     * Consumes the challenge, verifies the TLS bind signature, then maps state to a token result.
     */
    @Transactional
    public TokenPollResult token(
            UUID deviceId,
            String challenge,
            String signature,
            String tlsFingerprint,
            Integer tlsPort,
            String nexusLeafSha256,
            long nowMs) {
        ChallengeStore.ConsumeResult consumed =
                challengeStore.consume(deviceId != null ? deviceId.toString() : "", challenge, nowMs);
        if (consumed != ChallengeStore.ConsumeResult.OK) {
            return TokenPollResult.error(HttpStatus.UNAUTHORIZED, "invalid_grant", null);
        }
        Device device = deviceService.findOptional(deviceId).orElse(null);
        if (device == null) {
            return TokenPollResult.error(HttpStatus.FORBIDDEN, "access_denied", null);
        }
        String ownLeaf = NexusTlsFingerprint.canonical(nexusTlsCert.getLeaf());
        if (!DeviceSignatureVerifier.tlsListed(nexusLeafSha256)) {
            return TokenPollResult.error(HttpStatus.UNAUTHORIZED, "invalid_grant", null);
        }
        boolean tlsBind = DeviceSignatureVerifier.tlsListed(tlsFingerprint) || tlsPort != null;
        boolean ok = DeviceSignatureVerifier.verify(
                device.getPublicKey(),
                deviceId.toString(),
                challenge,
                signature,
                tlsFingerprint,
                tlsPort,
                ownLeaf);
        if (!ok || !ownLeaf.equals(nexusLeafSha256)) {
            return TokenPollResult.error(HttpStatus.UNAUTHORIZED, "invalid_grant", null);
        }
        int interval = intervalSec.getOrDefault(deviceId, EnrollmentStateRule.DEFAULT_INTERVAL_SECONDS);
        Long last = lastPollMs.get(deviceId);
        if (EnrollmentStateRule.isSlowDown(nowMs, last, interval)) {
            int next = EnrollmentStateRule.bumpInterval(interval);
            intervalSec.put(deviceId, next);
            lastPollMs.put(deviceId, nowMs);
            return TokenPollResult.error(HttpStatus.BAD_REQUEST, "slow_down", next);
        }
        lastPollMs.put(deviceId, nowMs);
        EnrollmentStateRule.Outcome outcome = EnrollmentStateRule.mapState(device.getState());
        if (outcome == EnrollmentStateRule.Outcome.REJECTED
                || outcome == EnrollmentStateRule.Outcome.UNKNOWN) {
            return TokenPollResult.error(HttpStatus.FORBIDDEN, "access_denied", null);
        }
        if (outcome == EnrollmentStateRule.Outcome.PENDING) {
            return TokenPollResult.error(HttpStatus.BAD_REQUEST, "authorization_pending", interval);
        }
        if (device.getPublicId() == null) {
            return TokenPollResult.error(HttpStatus.FORBIDDEN, "access_denied", null);
        }
        if (tlsBind) {
            applyTls(device, tlsFingerprint, tlsPort);
            deviceService.saveDevice(device);
        }
        String authToken = deviceTokenService.generateAuthToken(device.getId());
        String publicToken = deviceTokenService.generatePublicToken(device.getId(), device.getPublicId());
        DeviceTokenResponse body = DeviceTokenResponse.builder()
                .deviceId(device.getId())
                .approvedName(device.getName())
                .authToken(authToken)
                .publicToken(publicToken)
                .publicId(device.getPublicId())
                .state(DeviceState.APPROVED)
                .nexusPublicKey(rsaKeyConfig.getPublicKeyBase64())
                .build();
        return TokenPollResult.ok(body);
    }

    /**
     * Result of a token poll. Body set only on 200.
     */
    public record TokenPollResult(
            HttpStatus status,
            String error,
            Integer interval,
            DeviceTokenResponse body) {

        static TokenPollResult ok(DeviceTokenResponse body) {
            return new TokenPollResult(HttpStatus.OK, null, null, body);
        }

        static TokenPollResult error(HttpStatus status, String error, Integer interval) {
            return new TokenPollResult(status, error, interval, null);
        }
    }

    /**
     * Expected enrollment HTTP error.
     */
    public static class EnrollmentDeniedException extends RuntimeException {
        private final HttpStatus status;
        private final String error;

        public EnrollmentDeniedException(HttpStatus status, String error) {
            this(status, error, null);
        }

        public EnrollmentDeniedException(HttpStatus status, String error, Throwable cause) {
            super(error, cause);
            this.status = status;
            this.error = error;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }
    }

    /**
     * Register signature required when TLS fields are present. Phone sends JSON null, no signature.
     */
    private static void verifyRegisterBind(DeviceRegistrationRequest request) {
        boolean tlsBind = DeviceSignatureVerifier.tlsListed(request.getTlsFingerprint())
                || request.getTlsPort() != null;
        if (!tlsBind) {
            return;
        }
        if (!DeviceSignatureVerifier.verifyRegister(
                request.getPublicKey(),
                request.getTlsFingerprint(),
                request.getTlsPort(),
                request.getSignature())) {
            throw new EnrollmentDeniedException(HttpStatus.UNAUTHORIZED, "invalid_grant");
        }
    }

    /**
     * Stores listed TLS fields. JSON null leaves the previous values on re-register.
     */
    private static void applyTls(Device device, String tlsFingerprint, Integer tlsPort) {
        if (DeviceSignatureVerifier.tlsListed(tlsFingerprint)) {
            device.setTlsFingerprint(tlsFingerprint);
        }
        if (tlsPort != null) {
            device.setTlsPort(tlsPort);
        }
    }

    private static String blankToNull(String value) {
        return DeviceSignatureVerifier.tlsListed(value) ? value : null;
    }
}
