package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Detects devices that dropped without a clean WS close (power loss, network cut, sleep).
 * Heartbeat interval: 15s (device side)
 * Zombie threshold:   35s - 2 missed heartbeats + buffer
 * This check runs:    every 20s
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceHeartbeatMonitor {

    private static final int ZOMBIE_THRESHOLD_SECONDS = 35;

    private final DeviceWebSocketHandler webSocketHandler;
    private final DeviceService deviceService;

    /**
     * Session appears open but device hasn't heartbeat within threshold.
     * Force-close it, afterConnectionClosed will handle the offline transition.
     */
    @Scheduled(fixedDelay = 20_000)
    public void detectZombieSessions() {
        Instant threshold = Instant.now().minus(ZOMBIE_THRESHOLD_SECONDS, ChronoUnit.SECONDS);

        List<Device> stale = deviceService.findApprovedLastSeenBefore(threshold);

        for (Device device : stale) {
            UUID deviceId = device.getId();

            if (!webSocketHandler.isConnected(deviceId)) continue;


            log.warn("Zombie WS session detected for device {}, last seen at {}. Force-closing.",
                    deviceId, device.getLastSeenAt());

            forceClose(deviceId);
        }
    }
    /**
     * Closing the session triggers afterConnectionClosed in DeviceWebSocketHandler,
     * which calls markOffline and broadcastDeviceOffline
     */
    private void forceClose(UUID deviceId) {

        WebSocketSession session = webSocketHandler.getSession(deviceId);
        if (session == null) return;

        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException e) {
            log.warn("Failed to force-close zombie session for device {}: {}", deviceId, e.getMessage());

            webSocketHandler.handleZombieCleanup(deviceId, session);
        }
    }
}