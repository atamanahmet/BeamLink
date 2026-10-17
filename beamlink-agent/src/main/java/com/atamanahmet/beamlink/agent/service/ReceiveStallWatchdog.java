package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.constants.TransferConstants;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pauses stalled receive transfers through the normal pause path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReceiveStallWatchdog {

    private final ReceiveStallTracker receiveStallTracker;
    private final FileTransferRepository fileTransferRepository;
    private final TransferSideResolver sideResolver;
    private final TransferManagementService transferManagementService;

    /**
     * Checks tracked receives every 15 seconds for idle stalls.
     * Re-checks last touch right before pause so a late chunk cancels it.
     */
    @Scheduled(fixedDelay = 15_000)
    public void checkStalls() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, Instant> entry : receiveStallTracker.snapshot().entrySet()) {
            UUID transferId = entry.getKey();
            FileTransfer ft = fileTransferRepository.findByTransferId(transferId).orElse(null);
            if (ft == null) {
                receiveStallTracker.remove(transferId);
                continue;
            }
            PauseOwner side = sideResolver.resolve(
                    ft.getSourceAgentId(), ft.getTargetAgentId(), ft.getTargetIp());
            if (side != PauseOwner.TARGET) {
                receiveStallTracker.remove(transferId);
                continue;
            }
            if (!ReceiveStallRule.isStalled(
                    ft.getStatus(),
                    entry.getValue(),
                    now,
                    TransferConstants.STALL_TIMEOUT_SECONDS)) {
                continue;
            }
            Instant latest = receiveStallTracker.getLastTouch(transferId);
            Instant recheckNow = Instant.now();
            if (latest == null || !ReceiveStallRule.isStalled(
                    ft.getStatus(),
                    latest,
                    recheckNow,
                    TransferConstants.STALL_TIMEOUT_SECONDS)) {
                continue;
            }
            try {
                transferManagementService.pauseSingle(transferId);
                log.warn("Receive stalled, paused transferId={}", transferId);
            } catch (Exception e) {
                log.warn("Receive stall pause failed for {}: {}", transferId, e.getMessage());
            }
            receiveStallTracker.remove(transferId);
        }
    }
}
