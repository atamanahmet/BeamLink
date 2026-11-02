package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.domain.Device;
import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import com.atamanahmet.beamlink.peer.dto.DeviceIdentityResponse;
import com.atamanahmet.beamlink.peer.dto.DeviceRenameRequest;
import com.atamanahmet.beamlink.peer.dto.DeviceStatusDTO;
import com.atamanahmet.beamlink.peer.event.DeviceApprovedEvent;
import com.atamanahmet.beamlink.peer.event.DeviceIdentityChangedEvent;
import com.atamanahmet.beamlink.peer.exception.NexusOfflineException;
import com.atamanahmet.beamlink.peer.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceConfig config;
    private final DeviceRepository deviceRepository;
    private final TransferLogService transferLogService;
    private final PeerCacheService peerCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final NexusAddressHolder nexusAddressHolder;
    private final WebClient nexusWebClient;

    private Device device;
    private final AtomicBoolean ipDetectWarnLogged = new AtomicBoolean(false);

    /**
     * Load device from db on startup, create if not exists
     */
    @PostConstruct
    public synchronized void init() {
        this.device = deviceRepository.findById(1L).orElseGet(() -> {
            Device a = new Device();
            a.setDeviceName("Device-" + config.getIp() + ":" + config.getDevicePort());
            a.setIpAddress(config.getIp());
            a.setPort(config.getDevicePort());
            a.setState(DeviceState.UNREGISTERED);
            log.info("No device record found, creating new device: {}", a.getDeviceName());
            return deviceRepository.save(a);
        });
        log.info("Device loaded: name={}, deviceId={}, state={}",
                device.getDeviceName(), device.getDeviceId(), device.getState());
    }

    private synchronized void persist() {
        this.device = deviceRepository.save(device);
    }

    public synchronized void transitionTo(DeviceState newState) {
        DeviceState current = device.getState();

        // don't allow going back UNREGISTERED once approved
        if (current == DeviceState.APPROVED && newState == DeviceState.UNREGISTERED) {
            log.warn("Ignoring invalid state transition {} -> {}", current, newState);
            return;
        }

        if (newState == DeviceState.PENDING_APPROVAL || newState == DeviceState.REJECTED) {
            device.setAuthToken(null);
            device.setPublicToken(null);
        }

        log.info("Device state: {} -> {}", current, newState);
        device.setState(newState);
        persist();
    }

    public synchronized void forceReset() {
        if (device.getState() == DeviceState.UNREGISTERED && device.getDeviceId() == null) {
            log.debug("Device already in clean UNREGISTERED state, skipping reset.");
            return;
        }

        device.setDeviceId(null);
        device.setState(DeviceState.UNREGISTERED);
        device.setAuthToken(null);
        device.setPublicToken(null);
        persist();
        log.info("Device force reset to UNREGISTERED.");
    }

    /**
     * Clears identity fields used for nexus registration.
     */
    public synchronized void clearIdentity() {
        device.setDeviceId(null);
        device.setPublicId(null);
        device.setAuthToken(null);
        device.setPublicToken(null);
        device.setNexusPublicKey(null);
        device.setState(DeviceState.UNREGISTERED);
        persist();
        log.info("Device identity cleared.");
    }

    /**
     * Reloads or creates the singleton device row after a factory reset.
     */
    public synchronized void reloadFresh() {
        init();
    }

    /**
     * Deletes every device row.
     */
    public synchronized void deleteAll() {
        deviceRepository.deleteAll();
        log.info("All devices deleted.");
    }

    public synchronized void storeTokens(String authToken, String publicToken) {
        device.setAuthToken(authToken);
        device.setPublicToken(publicToken);
        persist();
        log.debug("Tokens stored.");
    }

    /**
     * Stores tokens from a successful /token poll.
     */
    public synchronized void applyIssuedTokens(
            UUID deviceId,
            String approvedName,
            String authToken,
            String publicToken,
            UUID publicId,
            String nexusPublicKey) {
        if (deviceId != null) {
            device.setDeviceId(deviceId);
        }
        if (approvedName != null && !approvedName.isBlank()) {
            device.setDeviceName(approvedName);
        }
        device.setAuthToken(authToken);
        device.setPublicToken(publicToken);
        device.setPublicId(publicId);
        device.setNexusPublicKey(nexusPublicKey);
        device.setState(DeviceState.APPROVED);
        persist();
        log.info("Device approved. Name={}, DeviceId={}", device.getDeviceName(), device.getDeviceId());
        eventPublisher.publishEvent(new DeviceApprovedEvent(this));
    }

    /**
     * Applies public facts from identify. Tokens come only from /token poll.
     */
    public synchronized void applyNexusIdentity(DeviceIdentityResponse response) {
        boolean identityChanged = !response.getDeviceId().equals(device.getDeviceId())
                || !response.getDeviceName().equals(device.getDeviceName())
                || !Objects.equals(response.getPublicId(), device.getPublicId())
                || response.getState() != device.getState();

        device.setDeviceId(response.getDeviceId());
        device.setDeviceName(response.getDeviceName());
        device.setPublicId(response.getPublicId());
        DeviceState next = response.getState();
        if (next == DeviceState.APPROVED && device.getAuthToken() == null) {
            next = DeviceState.PENDING_APPROVAL;
        }
        device.setState(next);

        if (response.getNexusPublicKey() != null) {
            device.setNexusPublicKey(response.getNexusPublicKey());
        }

        persist();
        log.info("Identity applied. Name={}, DeviceId={}, changed={}",
                device.getDeviceName(), device.getDeviceId(), identityChanged);

        if (identityChanged) {
            eventPublisher.publishEvent(new DeviceIdentityChangedEvent(this));
        }
    }

    public void requestRename(UUID deviceId, String newName) {
        String nexusUrl = nexusAddressHolder.getNexusUrl();
        if (nexusUrl == null) {
            throw new NexusOfflineException("Nexus not reachable, URL not resolved");
        }

        nexusWebClient.post()
                .uri(nexusUrl + "/api/nexus/device/" + deviceId + "/rename")
                .header("X-Auth-Token", getAuthToken())
                .bodyValue(new DeviceRenameRequest(newName))
                .retrieve()
                .toBodilessEntity()
                .subscribeOn(Schedulers.boundedElastic())
                .block();
    }

    public synchronized void updateDeviceName(String newName) {
        device.setDeviceName(newName);
        persist();
        log.info("Device renamed to: {}", newName);
    }

    public synchronized void updateDeviceId(UUID newDeviceId) {
        device.setDeviceId(newDeviceId);
        persist();
        log.info("Device updated deviceId to: {}", newDeviceId);
    }

    public boolean isApproved() {
        return device.isApproved();
    }

    public DeviceState getState() {
        return device.getState();
    }

    public UUID getDeviceId() {
        return device.getDeviceId();
    }

    public String getDeviceName() {
        return device.getDeviceName();
    }

    public String getAuthToken() {
        return device.getAuthToken();
    }

    public String getPublicToken() {
        return device.getPublicToken();
    }

    public String getDeviceIp() {return device.getIpAddress();}
    public Integer getDevicePort() {return device.getPort();}

    /**
     * Picks IP to store. Null or blank detected keeps the old value.
     */
    static String chooseIpAddress(String stored, String detected) {
        if (detected == null || detected.isBlank()) {
            return stored;
        }
        return detected;
    }

    /**
     * Re-reads current IP and persists when it differs from stored.
     */
    public synchronized void refreshStoredIpAddress() {
        String detected;
        if ("auto".equalsIgnoreCase(config.getConfiguredIp())) {
            detected = config.getNetworkConfig().detectCurrentIp();
        } else {
            detected = config.getConfiguredIp();
        }

        if (detected == null || detected.isBlank()) {
            if (ipDetectWarnLogged.compareAndSet(false, true)) {
                log.warn("IP detect failed, keeping stored address {}", device.getIpAddress());
            }
            return;
        }

        String next = chooseIpAddress(device.getIpAddress(), detected);
        if (!Objects.equals(device.getIpAddress(), next)) {
            device.setIpAddress(next);
            persist();
            log.info("Device IP updated to {}", next);
        }
    }

    public Device getDevice() {
        return device;
    }

    public DeviceStatusDTO getDeviceStatusDTO() {
        return DeviceStatusDTO.builder()
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .ipAddress(device.getIpAddress())
                .port(device.getPort())
                .unSyncedLogs(transferLogService.getUnsyncedLogs().size())
                .peerVersion(peerCacheService.getCurrentPeerListVersion())
                .build();
    }

    public String getNexusPublicKey() {
        return device.getNexusPublicKey();
    }

    public UUID getPublicId() {
        return device.getPublicId();
    }
}