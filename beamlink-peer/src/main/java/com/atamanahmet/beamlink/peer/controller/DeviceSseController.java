package com.atamanahmet.beamlink.peer.controller;

import com.atamanahmet.beamlink.peer.exception.ErrorReason;
import com.atamanahmet.beamlink.peer.exception.ProblemDetails;
import com.atamanahmet.beamlink.peer.event.DeviceIdentityEventListener;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.atamanahmet.beamlink.peer.service.SseEmitters;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device")
public class DeviceSseController {

    private final SseEmitters sseEmitters;
    private final DeviceService deviceService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sseEmitters.add(emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("identity_updated")
                    .data(DeviceIdentityEventListener.identityUpdatedPayload(deviceService.getDevice())));
        } catch (Exception e) {
            log.warn("Failed to send initial identity to new SSE client", e);
        }

        return emitter;
    }

    @GetMapping("/token")
    public ResponseEntity<?> getPublicToken() {
        String token = deviceService.getPublicToken();
        log.info("Token requested. publicToken={}, state={}",
                token != null ? "present" : "null",
                deviceService.getState());
        if (token == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    ProblemDetails.of(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Service unavailable",
                            "Public token not available",
                            "TOKEN_UNAVAILABLE",
                            ErrorReason.UNKNOWN));
        }
        return ResponseEntity.ok(token);
    }
}