package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.config.NexusMdnsDiscovery;
import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import com.atamanahmet.beamlink.peer.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.dto.DeviceResetResponse;
import com.atamanahmet.beamlink.peer.exception.ResetBusyException;
import com.atamanahmet.beamlink.peer.security.DeviceKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetService {

    private static final Duration UNREGISTER_TIMEOUT = Duration.ofSeconds(3);
    private static final String BUSY_MESSAGE =
            "Cannot reset while a transfer is ACTIVE or PENDING.";

    private final FileTransferService fileTransferService;
    private final BatchTransferService batchTransferService;
    private final DirectoryTransferService directoryTransferService;
    private final TransferLogService transferLogService;
    private final SettingsService settingsService;
    private final DeviceService deviceService;
    private final PeerCacheService peerCacheService;
    private final NetworkRegistrationService networkRegistrationService;
    private final NexusAddressHolder nexusAddressHolder;
    private final NexusWebSocketService nexusWebSocketService;
    private final NexusFirstContactService firstContact;
    private final NexusMdnsDiscovery mdnsDiscovery;
    private final StorageService storageService;
    private final ReceiveFileChannels receiveFileChannels;
    private final SpeedLimitSettingsCache speedLimitSettingsCache;
    private final WebClient nexusWebClient;
    private final DeviceKey deviceKey;

    /**
     * Best effort unregister on nexus, then clear identity and register again.
     */
    public DeviceResetResponse reRegister(boolean force) {
        refuseIfBusy(force);
        boolean nexusRemoved = tryUnregisterOnNexus();
        nexusWebSocketService.disconnect();
        deviceService.clearIdentity();
        peerCacheService.clearCache();
        networkRegistrationService.registerWithNexus();
        DeviceState state = deviceService.getState();
        String message = buildReRegisterMessage(nexusRemoved, state);
        log.info("Re-register finished. nexusRemoved={}, state={}", nexusRemoved, state);
        return new DeviceResetResponse(nexusRemoved, state, message);
    }

    /**
     * Deletes all device data rows, clears memory and temp files, then reloads device.
     */
    @Transactional
    public void factoryReset(boolean force) {
        refuseIfBusy(force);
        nexusWebSocketService.disconnect();
        receiveFileChannels.closeAll();
        fileTransferService.deleteAll();
        directoryTransferService.deleteAll();
        batchTransferService.deleteAll();
        transferLogService.deleteAll();
        settingsService.deleteAll();
        deviceService.deleteAll();
        nexusAddressHolder.clear();
        firstContact.resetMemory();
        mdnsDiscovery.startOrRestart();
        peerCacheService.clearCache();
        speedLimitSettingsCache.setFileDefaultMbps(null);
        speedLimitSettingsCache.setTotalMbps(null);
        storageService.clearTempContents();
        deviceKey.deleteAndRecreate();
        deviceService.reloadFresh();
        log.info("Device factory reset complete.");
    }

    /**
     * 409 when ACTIVE or PENDING exist, unless force skips the busy rule.
     */
    private void refuseIfBusy(boolean force) {
        List<String> statuses = blockingStatuses();
        int count = ResetBusyRule.blockingCount(statuses);
        if (ResetBusyRule.isBusy(statuses, force)) {
            throw new ResetBusyException(BUSY_MESSAGE, count);
        }
        if (force) {
            log.info("Reset forced. Busy rule skipped. blockingCount={}", count);
        }
    }

    /**
     * One ACTIVE or PENDING name per blocking transfer. PAUSED is omitted.
     */
    private List<String> blockingStatuses() {
        List<String> statuses = new ArrayList<>();
        addCopies(statuses, TransferStatus.ACTIVE.name(),
                fileTransferService.countByStatus(TransferStatus.ACTIVE));
        addCopies(statuses, TransferStatus.PENDING.name(),
                fileTransferService.countByStatus(TransferStatus.PENDING));
        addCopies(statuses, GroupTransferStatus.ACTIVE.name(),
                batchTransferService.countByStatus(GroupTransferStatus.ACTIVE));
        addCopies(statuses, GroupTransferStatus.PENDING.name(),
                batchTransferService.countByStatus(GroupTransferStatus.PENDING));
        addCopies(statuses, GroupTransferStatus.ACTIVE.name(),
                directoryTransferService.countByStatus(GroupTransferStatus.ACTIVE));
        addCopies(statuses, GroupTransferStatus.PENDING.name(),
                directoryTransferService.countByStatus(GroupTransferStatus.PENDING));
        return statuses;
    }

    /**
     * Appends status once per matching transfer row.
     */
    private static void addCopies(List<String> statuses, String status, long count) {
        for (long i = 0; i < count; i++) {
            statuses.add(status);
        }
    }

    /**
     * Calls nexus to remove this device. Failure is logged and never fatal.
     */
    private boolean tryUnregisterOnNexus() {
        String url = nexusAddressHolder.getNexusUrl();
        String token = deviceService.getAuthToken();
        if (url == null || token == null || token.isBlank()) {
            log.info("Skip nexus unregister, missing URL or token.");
            return false;
        }
        try {
            nexusWebClient.delete()
                    .uri(url + "/api/devices/me")
                    .header("X-Auth-Token", token)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(UNREGISTER_TIMEOUT)
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();
            log.info("Nexus removed this device.");
            return true;
        } catch (Exception e) {
            log.warn("Self-unregister on nexus failed: {}", e.getMessage());
            return false;
        }
    }

    private static String buildReRegisterMessage(boolean nexusRemoved, DeviceState state) {
        String hint = nexusRemoved
                ? "nexusRemoved=true. Nexus removed this device."
                : "nexusRemoved=false. Nexus did not remove the old record.";
        return "Identity wiped. Device will connect to a nexus again. State "
                + state + ". " + hint;
    }
}
