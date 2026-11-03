package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import com.atamanahmet.beamlink.peer.dto.DevicePingResponse;
import com.atamanahmet.beamlink.peer.dto.StatusUpdateResponse;
import com.atamanahmet.beamlink.peer.event.DeviceApprovedEvent;
import com.atamanahmet.beamlink.peer.event.NexusEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusStatusService {

    private final DeviceService deviceService;
    private final NexusWebSocketService wsService;
    private final NexusEventPublisher nexusEventPublisher;
    private final NexusConnectionStateService connectionState;
    private final NetworkRegistrationService networkRegistrationService;
    private final NexusAddressHolder nexusAddressHolder;
    private final PeerCacheService peerCacheService;
    private final DeviceResumePolicyHolder resumePolicyHolder;
    private final WebClient nexusWebClient;
    private final NexusTlsPin nexusTlsPin;

    @EventListener
    public void onDeviceApproved(DeviceApprovedEvent event) {
        if (!nexusAddressHolder.isResolved() || !nexusTlsPin.hasPin()) {
            log.warn("Device approved event received but Nexus URL or TLS pin not ready. Cannot connect WebSocket.");
            return;
        }
        log.info("Device approval received. Connecting WebSocket in 2s...");
        Mono.delay(Duration.ofSeconds(2))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(l -> log.info("Attempting post-approval WS connect..."))
                .doOnError(err -> log.warn("Post-approval WS connect failed: {}", err.getMessage()))
                .onErrorComplete()
                .subscribe(l -> wsService.connect(nexusAddressHolder.getNexusUrl()));
    }

    /**
     * Periodically checks if device is registered/approved.
     * HTTP status/log updates are skipped once WS is active.
     */
    @Scheduled(fixedRate = 30_000)
    public void ensureRegistered() {
        if (!nexusAddressHolder.isResolved() || !nexusTlsPin.hasPin()) {
            log.debug("Nexus URL or TLS pin not ready. Skipping registration check.");
            return;
        }

        DeviceState state = deviceService.getState();

        if (state == DeviceState.UNREGISTERED) {
            log.info("Device unregistered. Attempting registration...");
            if (!isNexusReachable()) {
                connectionState.reportOffline();
            } else {
                connectionState.reportOnline();
                if (UnregisteredEnrollRule.shouldEnroll(
                        nexusAddressHolder.isResolved(),
                        nexusTlsPin.hasPin(),
                        state,
                        networkRegistrationService.isRegistrationInProgress())) {
                    networkRegistrationService.registerWithNexus();
                } else {
                    log.debug("Registration already triggered by online event. Skipping.");
                }
            }

        } else if (state == DeviceState.PENDING_APPROVAL) {
            log.info("Device pending approval. Checking if Nexus knows device...");
            deviceService.refreshStoredIpAddress();
            if (!isNexusReachable()) {
                connectionState.reportOffline();
            } else {
                connectionState.reportOnline();
                if (!checkIfNexusKnowsDevice()) {
                    log.warn("Nexus does not recognize device ID. Nexus DB may have been wiped.");
                    nexusEventPublisher.publishLostDevice("Nexus lost device record");
                }
            }

        } else if (state == DeviceState.APPROVED && !wsService.isConnected()) {
            log.info("Device approved but WS disconnected. Reconnecting...");
            wsService.connect(nexusAddressHolder.getNexusUrl());
        }
    }

    private boolean isNexusReachable() {
        try {
            DevicePingResponse ping = nexusWebClient.get()
                    .uri(nexusAddressHolder.getNexusUrl() + "/api/devices/ping")
                    .retrieve()
                    .bodyToMono(DevicePingResponse.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(5))
                    .block();
            if (ping != null) {
                resumePolicyHolder.updateFromPing(ping.resumePolicy());
            }
            return true;
        } catch (Exception e) {
            log.debug("Nexus ping failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkIfNexusKnowsDevice() {
        UUID deviceId = deviceService.getDeviceId();
        if (deviceId == null) {
            log.debug("No device ID yet, skipping existence check.");
            return false;
        }
        try {
            nexusWebClient.get()
                    .uri(nexusAddressHolder.getNexusUrl() + "/api/devices/" + deviceId + "/exists")
                    .retrieve()
                    .toBodilessEntity()
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Nexus does not know device: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 401/403 on an APPROVED device: one token recovery. 404: lost-device.
     */
    private void handleAuthError(int status, String context) {
        if (status == 401 || status == 403) {
            if (deviceService.isApproved()) {
                log.warn("{} rejected [{}]. Token recovery.", context, status);
                networkRegistrationService.recoverAfterAuthLoss();
                return;
            }
            log.warn("{} rejected [{}]. Not approved. Stop.", context, status);
            return;
        }
        log.warn("{} rejected [{}]. Publishing lost-device event.", context, status);
        peerCacheService.markTokenRejected(status);
        nexusEventPublisher.publishLostDevice(context + " rejected with " + status);
    }

    /**
     * HTTP status report fallback (skipped once WS is connected).
     */
    @Scheduled(fixedRate = 30_000)
    public void reportStatus() {
        if (!nexusAddressHolder.isResolved() || !deviceService.isApproved()) return;

        if (wsService.isConnected()) {
            log.debug("WebSocket active, skipping HTTP status report.");
            return;
        }

        deviceService.refreshStoredIpAddress();

        try {
            StatusUpdateResponse response = nexusWebClient.post()
                    .uri(nexusAddressHolder.getNexusUrl() + "/api/devices/status")
                    .header("X-Auth-Token", deviceService.getAuthToken())
                    .bodyValue(deviceService.getDeviceStatusDTO())
                    .retrieve()
                    .bodyToMono(StatusUpdateResponse.class)
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (response != null && response.peerOutdated() && response.peers() != null) {
                peerCacheService.updatePeers(response.peers(), -1L);
                log.info("Peer list refreshed via HTTP fallback ({} peers)", response.peers().size());
            }

            log.debug("HTTP status reported to Nexus (fallback)");

        } catch (WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 401 || status == 403) {
                handleAuthError(status, "HTTP status");
            } else {
                log.warn("Unexpected HTTP status response [{}]", status);
            }
        } catch (Exception e) {
            log.warn("HTTP status report failed: {}", e.getMessage());
        }
    }
}