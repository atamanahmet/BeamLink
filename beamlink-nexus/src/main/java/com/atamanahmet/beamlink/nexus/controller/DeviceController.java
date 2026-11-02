package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.dto.DeviceIdentityResponse;
import com.atamanahmet.beamlink.nexus.dto.DevicePingResponse;
import com.atamanahmet.beamlink.nexus.dto.DeviceRegistrationRequest;
import com.atamanahmet.beamlink.nexus.dto.DeviceRegistrationResponse;
import com.atamanahmet.beamlink.nexus.dto.DeviceStatusRequest;
import com.atamanahmet.beamlink.nexus.dto.DeviceStatusResponse;
import com.atamanahmet.beamlink.nexus.dto.DeviceChallengeRequest;
import com.atamanahmet.beamlink.nexus.dto.DeviceChallengeResponse;
import com.atamanahmet.beamlink.nexus.dto.DeviceTokenRequest;
import com.atamanahmet.beamlink.nexus.dto.EnrollmentErrorBody;
import com.atamanahmet.beamlink.nexus.config.RsaKeyConfig;
import com.atamanahmet.beamlink.nexus.security.LoopbackAddresses;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import com.atamanahmet.beamlink.nexus.service.DeviceEnrollmentService;
import com.atamanahmet.beamlink.nexus.service.EnrollmentThrottle;
import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.ProblemDetails;
import com.atamanahmet.beamlink.nexus.service.NexusResumePolicyStore;
import com.atamanahmet.beamlink.nexus.websocket.DeviceWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

        private final DeviceService deviceService;
        private final DeviceEnrollmentService deviceEnrollmentService;
        private final EnrollmentThrottle enrollmentThrottle;
        private final NexusResumePolicyStore resumePolicyStore;
        private final RsaKeyConfig rsaKeyConfig;
        private final DeviceWebSocketHandler deviceWebSocketHandler;

        /**
         * Removes only the calling device. Auth token must match the caller.
         */
        @DeleteMapping("/me")
        public ResponseEntity<?> unregisterSelf(Authentication authentication) {
                if (authentication == null || !(authentication.getPrincipal() instanceof UUID deviceId)) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                ProblemDetails.of(
                                        HttpStatus.UNAUTHORIZED,
                                        "Unauthorized",
                                        "Authentication required",
                                        "UNAUTHORIZED",
                                        ErrorReason.UNKNOWN));
                }
                deviceWebSocketHandler.closeSession(deviceId);
                deviceService.deleteDevice(deviceId);
                return ResponseEntity.noContent().build();
        }

        /**
         * Device registration with device public key.
         */
        @PostMapping("/register")
        public ResponseEntity<?> register(
                        @RequestBody DeviceRegistrationRequest request,
                        HttpServletRequest http) {
                ResponseEntity<EnrollmentErrorBody> throttled = throttle(http);
                if (throttled != null) {
                        return throttled;
                }
                log.info("Device register request: {}:{}", request.getIpAddress(), request.getPort());
                try {
                        DeviceRegistrationResponse body =
                                deviceEnrollmentService.register(request, http.getRemoteAddr());
                        enrollmentThrottle.clear(http.getRemoteAddr());
                        return ResponseEntity.ok(body);
                } catch (DeviceEnrollmentService.EnrollmentDeniedException e) {
                        enrollmentThrottle.recordFailure(http.getRemoteAddr(), System.currentTimeMillis());
                        throw e;
                }
        }

        /**
         * One-time enrollment challenge bound to deviceId.
         */
        @PostMapping("/challenge")
        public ResponseEntity<?> challenge(
                        @RequestBody DeviceChallengeRequest request,
                        HttpServletRequest http) {
                ResponseEntity<EnrollmentErrorBody> throttled = throttle(http);
                if (throttled != null) {
                        return throttled;
                }
                try {
                        DeviceChallengeResponse body = deviceEnrollmentService.challenge(
                                request.getDeviceId(), System.currentTimeMillis());
                        return ResponseEntity.ok(body);
                } catch (DeviceEnrollmentService.EnrollmentDeniedException e) {
                        enrollmentThrottle.recordFailure(http.getRemoteAddr(), System.currentTimeMillis());
                        throw e;
                }
        }

        /**
         * Token poll. Challenge is single use even on failure.
         */
        @PostMapping("/token")
        public ResponseEntity<?> token(
                        @RequestBody DeviceTokenRequest request,
                        HttpServletRequest http) {
                ResponseEntity<EnrollmentErrorBody> throttled = throttle(http);
                if (throttled != null) {
                        return throttled;
                }
                DeviceEnrollmentService.TokenPollResult result = deviceEnrollmentService.token(
                        request.getDeviceId(),
                        request.getChallenge(),
                        request.getSignature(),
                        request.getTlsFingerprint(),
                        request.getTlsPort(),
                        request.getNexusLeafSha256(),
                        System.currentTimeMillis());
                if (result.body() != null) {
                        enrollmentThrottle.clear(http.getRemoteAddr());
                        return ResponseEntity.ok(result.body());
                }
                if ("invalid_grant".equals(result.error()) || "access_denied".equals(result.error())) {
                        enrollmentThrottle.recordFailure(http.getRemoteAddr(), System.currentTimeMillis());
                }
                return ResponseEntity.status(result.status())
                        .body(result.interval() != null
                                ? EnrollmentErrorBody.of(result.error(), result.interval())
                                : EnrollmentErrorBody.of(result.error()));
        }

        /**
         * Device heartbeat / status update
         */
        @PostMapping("/status")
        public ResponseEntity<DeviceStatusResponse> updateStatus(
                        @RequestBody DeviceStatusRequest request) {

                return ResponseEntity.ok(deviceService.updateDeviceStatus(request));
        }

        /**
         * Checks if device still exists in DB.
         * Used by device on startup to detect DB resets.
         */
        @GetMapping("/{id}/exists")
        public ResponseEntity<Void> exists(@PathVariable UUID id) {
                deviceService.findByDeviceId(id);
                return ResponseEntity.ok().build();
        }

        @GetMapping("/ping")
        public ResponseEntity<DevicePingResponse> ping() {
                return ResponseEntity.ok(
                                new DevicePingResponse(resumePolicyStore.getMode().name()));
        }

        /**
         * Device identity lookup by ip:port. Public facts only. No tokens.
         */
        @GetMapping("/identify")
        public ResponseEntity<?> identify(
                        @RequestParam String ipAddress,
                        @RequestParam int port,
                        HttpServletRequest request) {
                if (!LoopbackAddresses.sourceMatchesClaimed(ipAddress, request.getRemoteAddr())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                                ProblemDetails.of(
                                        HttpStatus.FORBIDDEN,
                                        "Forbidden",
                                        "Source address does not match claim",
                                        "FORBIDDEN",
                                        ErrorReason.UNKNOWN));
                }

                return deviceService.getByIpAddressAndPort(ipAddress, port)
                        .<ResponseEntity<?>>map(device -> ResponseEntity.ok(DeviceIdentityResponse.builder()
                                .deviceId(device.getId())
                                .deviceName(device.getName())
                                .state(device.getState())
                                .publicId(device.getPublicId())
                                .nexusPublicKey(device.getState() == DeviceState.APPROVED
                                                ? rsaKeyConfig.getPublicKeyBase64() : null)
                                .build()))
                        .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                                ProblemDetails.of(
                                        HttpStatus.NOT_FOUND,
                                        "Not found",
                                        "Device not found",
                                        "NOT_FOUND",
                                        ErrorReason.UNKNOWN)));
        }

        /**
         * 429 when this address is over the enrollment failure limit.
         */
        private ResponseEntity<EnrollmentErrorBody> throttle(HttpServletRequest http) {
                String remote = http.getRemoteAddr();
                long now = System.currentTimeMillis();
                if (enrollmentThrottle.isThrottled(remote, now)) {
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(EnrollmentErrorBody.of("slow_down"));
                }
                return null;
        }

}