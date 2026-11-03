package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.domain.Device;
import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import com.atamanahmet.beamlink.peer.dto.DeviceRegistrationRequest;
import com.atamanahmet.beamlink.peer.dto.DeviceRegistrationResponse;
import com.atamanahmet.beamlink.peer.security.DeviceTlsCert;
import com.atamanahmet.beamlink.peer.security.DeviceKey;
import com.atamanahmet.beamlink.peer.security.DeviceSignaturePayload;
import com.atamanahmet.beamlink.peer.security.NexusTlsFingerprint;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Register, challenge and token poll. Tokens go to DeviceService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceEnrollmentClient {

    private final DeviceConfig deviceConfig;
    private final DeviceService deviceService;
    private final DeviceKey deviceKey;
    private final DeviceTlsCert deviceTlsCert;
    private final WebClient nexusWebClient;
    private final NexusAddressHolder nexusAddressHolder;
    private final NexusTlsPin nexusTlsPin;

    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicLong lastRecoveryMs = new AtomicLong(0);
    private volatile int pollBase = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS;
    private volatile int pollSticky = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS;
    private volatile int pollFailures = 0;
    private volatile int pollSleep = EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS;

    /**
     * Register with the device public key then poll /token.
     */
    public void registerAndPoll() {
        if (!nexusAddressHolder.isResolved() || !nexusTlsPin.hasPin()) {
            log.warn("Nexus URL or TLS pin not ready. Cannot register.");
            return;
        }
        DeviceRegistrationResponse registered = postRegister();
        if (registered == null || registered.getDeviceId() == null) {
            log.warn("Register returned empty. Will retry next cycle.");
            return;
        }
        deviceService.updateDeviceId(registered.getDeviceId());
        deviceService.transitionTo(DeviceState.PENDING_APPROVAL);
        pollUntilSettled(registered.getDeviceId(), Math.max(registered.getInterval(), 1));
    }

    /**
     * One challenge and token when local APPROVED lost access. Not a register.
     */
    public void recoverOnce() {
        if (!RecoveryGate.canRecover(
                deviceService.isApproved(),
                System.currentTimeMillis(),
                lastRecoveryMs.get())) {
            return;
        }
        UUID deviceId = deviceService.getDeviceId();
        if (deviceId == null || !nexusAddressHolder.isResolved() || !nexusTlsPin.hasPin()) {
            return;
        }
        lastRecoveryMs.set(System.currentTimeMillis());
        TokenCall call = challengeAndToken(deviceId);
        applyTokenCall(call, false);
        if (EnrollmentPollRule.onError(call.status, call.error, false)
                == EnrollmentPollRule.Action.KEEP_PENDING) {
            deviceService.transitionTo(DeviceState.PENDING_APPROVAL);
            pollUntilSettled(deviceId, call.interval > 0 ? call.interval : EnrollmentPollRule.DEFAULT_INTERVAL_SECONDS);
        }
    }

    /**
     * Resume token poll when local state is pending. Keeps the last interval.
     */
    public void resumePollIfPending() {
        if (deviceService.getState() != DeviceState.PENDING_APPROVAL) {
            return;
        }
        UUID deviceId = deviceService.getDeviceId();
        if (deviceId == null || !nexusTlsPin.hasPin()) {
            return;
        }
        pollUntilSettled(deviceId, null);
    }

    /**
     * Polls /token on a daemon thread so startup does not block.
     * Null start keeps the last wait.
     */
    private void pollUntilSettled(UUID deviceId, Integer startInterval) {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        if (startInterval != null) {
            int base = Math.max(1, startInterval);
            pollBase = base;
            pollSticky = base;
            pollFailures = 0;
            pollSleep = base;
        }
        Thread t = new Thread(() -> {
            boolean grantRetried = false;
            try {
                while (true) {
                    sleepSeconds(pollSleep);
                    TokenCall call = challengeAndToken(deviceId);
                    EnrollmentPollRule.Action action = applyTokenCall(call, grantRetried);
                    if (action == EnrollmentPollRule.Action.RETRY_CHALLENGE_ONCE) {
                        grantRetried = true;
                        continue;
                    }
                    if (action == EnrollmentPollRule.Action.KEEP_PENDING) {
                        EnrollmentPollRule.Wait wait = EnrollmentPollRule.nextWait(
                                pollBase,
                                pollSticky,
                                pollFailures,
                                call.status,
                                call.error,
                                bound -> ThreadLocalRandom.current().nextInt(bound));
                        pollSticky = wait.stickySeconds();
                        pollFailures = wait.failureCount();
                        pollSleep = wait.sleepSeconds();
                        grantRetried = false;
                        continue;
                    }
                    return;
                }
            } finally {
                polling.set(false);
            }
        }, "enrollment-poll");
        t.setDaemon(true);
        t.start();
    }

    private EnrollmentPollRule.Action applyTokenCall(TokenCall call, boolean grantRetried) {
        if (call.ok) {
            deviceService.applyIssuedTokens(
                    call.deviceId,
                    call.approvedName,
                    call.authToken,
                    call.publicToken,
                    call.publicId,
                    call.nexusPublicKey);
            log.info("Enrollment tokens stored.");
            return EnrollmentPollRule.Action.STORE_TOKENS;
        }
        EnrollmentPollRule.Action action = EnrollmentPollRule.onError(call.status, call.error, grantRetried);
        if (action == EnrollmentPollRule.Action.SET_REJECTED_STOP) {
            deviceService.transitionTo(DeviceState.REJECTED);
            log.warn("Enrollment access_denied. Stop. Use Re-register.");
        } else if (action == EnrollmentPollRule.Action.KEEP_PENDING) {
            deviceService.transitionTo(DeviceState.PENDING_APPROVAL);
        } else if (action == EnrollmentPollRule.Action.STOP) {
            log.warn("Enrollment stopped. error={}", call.error);
        }
        return action;
    }

    private DeviceRegistrationResponse postRegister() {
        Device device = deviceService.getDevice();
        String publicKey = deviceKey.publicKeyBase64();
        String tlsFingerprint = NexusTlsFingerprint.of(deviceTlsCert.getLeaf());
        int tlsPort = deviceConfig.getTlsPort();
        String signature = deviceKey.sign(
                DeviceSignaturePayload.signedRegisterMessage(publicKey, tlsFingerprint, tlsPort)
                        .getBytes(StandardCharsets.UTF_8));
        DeviceRegistrationRequest body = DeviceRegistrationRequest.builder()
                .deviceName(device.getDeviceName())
                .ipAddress(device.getIpAddress())
                .port(device.getPort())
                .publicKey(publicKey)
                .tlsFingerprint(tlsFingerprint)
                .tlsPort(tlsPort)
                .signature(signature)
                .build();
        try {
            return nexusWebClient.post()
                    .uri(nexusAddressHolder.getNexusUrl() + "/api/devices/register")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(DeviceRegistrationResponse.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();
        } catch (Exception e) {
            log.warn("Register failed: {}", e.getMessage());
            return null;
        }
    }

    private TokenCall challengeAndToken(UUID deviceId) {
        try {
            JsonPost ch = postJson("/api/devices/challenge", Map.of("deviceId", deviceId.toString()));
            if (ch == null || ch.body() == null || ch.body().isNull() || !ch.body().hasNonNull("challenge")) {
                String error = errorField(ch == null ? null : ch.body());
                Integer status = ch == null ? null : ch.status();
                return TokenCall.fail(status, error, 0);
            }
            String challenge = ch.body().get("challenge").asText();
            String storedPin = nexusTlsPin.storedPin();
            if (storedPin == null || storedPin.isBlank()) {
                return TokenCall.fail(null, null, 0);
            }
            String nexusLeaf = storedPin.replace(" ", "").toLowerCase(Locale.ROOT);
            String tlsFingerprint = NexusTlsFingerprint.of(deviceTlsCert.getLeaf());
            int tlsPort = deviceConfig.getTlsPort();
            byte[] message = DeviceSignaturePayload.signedMessage(
                    deviceId.toString(), challenge, tlsFingerprint, tlsPort, nexusLeaf)
                    .getBytes(StandardCharsets.UTF_8);
            String signature = deviceKey.sign(message);
            JsonPost tok = postJson("/api/devices/token", Map.of(
                    "deviceId", deviceId.toString(),
                    "challenge", challenge,
                    "signature", signature,
                    "tlsFingerprint", tlsFingerprint,
                    "tlsPort", tlsPort,
                    "nexusLeafSha256", nexusLeaf));
            if (tok == null || tok.body() == null || tok.body().isNull()) {
                Integer status = tok == null ? null : tok.status();
                return TokenCall.fail(status, null, 0);
            }
            if (tok.body().hasNonNull("authToken")) {
                return TokenCall.ok(tok.body());
            }
            String error = errorField(tok.body());
            int interval = tok.body().path("interval").asInt(0);
            return TokenCall.fail(tok.status(), error, interval);
        } catch (Exception e) {
            log.warn("Challenge/token failed: {}", e.getMessage());
            return TokenCall.fail(null, null, 0);
        }
    }

    /**
     * Reads error when the JSON field is present and not blank.
     */
    private static String errorField(JsonNode body) {
        if (body == null || !body.hasNonNull("error")) {
            return null;
        }
        String error = body.get("error").asText(null);
        if (error == null || error.isBlank()) {
            return null;
        }
        return error;
    }

    private JsonPost postJson(String path, Object body) {
        return nexusWebClient.post()
                .uri(nexusAddressHolder.getNexusUrl() + path)
                .bodyValue(body)
                .exchangeToMono(res -> res.bodyToMono(JsonNode.class)
                        .defaultIfEmpty(tools.jackson.databind.node.NullNode.getInstance())
                        .map(node -> new JsonPost(res.statusCode().value(), node)))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(Math.max(0, seconds) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record JsonPost(int status, JsonNode body) {
    }

    private record TokenCall(
            boolean ok,
            Integer status,
            String error,
            int interval,
            UUID deviceId,
            String approvedName,
            String authToken,
            String publicToken,
            UUID publicId,
            String nexusPublicKey) {

        static TokenCall fail(Integer status, String error, int interval) {
            return new TokenCall(false, status, error, interval, null, null, null, null, null, null);
        }

        static TokenCall ok(JsonNode tok) {
            UUID id = tok.hasNonNull("deviceId") ? UUID.fromString(tok.get("deviceId").asText()) : null;
            UUID publicId = tok.hasNonNull("publicId") ? UUID.fromString(tok.get("publicId").asText()) : null;
            return new TokenCall(
                    true,
                    200,
                    null,
                    0,
                    id,
                    tok.path("approvedName").asText(null),
                    tok.path("authToken").asText(null),
                    tok.path("publicToken").asText(null),
                    publicId,
                    tok.path("nexusPublicKey").asText(null));
        }
    }
}
