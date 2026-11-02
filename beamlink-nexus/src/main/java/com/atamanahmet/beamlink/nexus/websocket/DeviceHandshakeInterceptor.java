package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import com.atamanahmet.beamlink.nexus.exception.ProblemDetailWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceHandshakeInterceptor implements HandshakeInterceptor {

    private final DeviceTokenService deviceTokenService;
    private final DeviceService deviceService;

    public static final String DEVICE_ID_ATTR = "deviceId";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = request.getHeaders().getFirst("X-Auth-Token");

        if (token == null || token.isBlank()) {
            log.warn("WS handshake rejected, missing X-Auth-Token");
            writeHandshakeError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Missing authentication token",
                    "UNAUTHORIZED");
            return false;
        }

        UUID deviceId;
        try {
            deviceId = deviceTokenService.extractDeviceId(token);
        } catch (InvalidTokenException e) {
            log.warn("WS handshake rejected, invalid token: {}", e.getMessage());
            writeHandshakeError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Invalid token",
                    "UNAUTHORIZED");
            return false;
        }

        Device device = deviceService.findOptional(deviceId).orElse(null);
        if (device == null) {
            log.warn("WS handshake rejected, unknown device: {}", deviceId);
            writeHandshakeError(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Unknown device",
                    "UNAUTHORIZED");
            return false;
        }

        if (device.getState() != DeviceState.APPROVED) {
            log.warn("WS handshake rejected, device {} not approved", deviceId);
            writeHandshakeError(
                    response,
                    HttpStatus.FORBIDDEN,
                    "Forbidden",
                    "Device not approved",
                    "FORBIDDEN");
            return false;
        }

        attributes.put(DEVICE_ID_ATTR, deviceId);
        log.info("WS handshake accepted for device {}", deviceId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }

    /**
     * Writes ProblemDetail. Keeps the HTTP status if the body write fails.
     */
    private void writeHandshakeError(
            ServerHttpResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code) {
        try {
            ProblemDetailWriter.write(response, status, title, detail, code, ErrorReason.UNKNOWN);
        } catch (IOException ignored) {
            response.setStatusCode(status);
        }
    }
}