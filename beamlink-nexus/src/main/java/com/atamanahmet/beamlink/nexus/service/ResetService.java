package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.exception.ResetBusyException;
import com.atamanahmet.beamlink.nexus.websocket.DeviceWebSocketHandler;
import com.atamanahmet.beamlink.nexus.websocket.NexusUiWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResetService {

    private static final String BUSY_MESSAGE =
            "Cannot reset while a transfer is ACTIVE or PENDING.";

    private final FileTransferService fileTransferService;
    private final BatchTransferService batchTransferService;
    private final DirectoryTransferService directoryTransferService;
    private final TransferLogService transferLogService;
    private final PendingRenameService pendingRenameService;
    private final DeviceService deviceService;
    private final DeviceWebSocketHandler deviceWebSocketHandler;
    private final NexusUiWebSocketHandler nexusUiWebSocketHandler;
    private final PeerListService peerListService;
    private final ReceiveFileChannels receiveFileChannels;
    private final LoginFailureThrottle loginFailureThrottle;
    private final NexusService nexusService;

    /**
     * Closes sessions, clears caches, deletes devices transfers and logs.
     */
    @Transactional
    public void resetNetworkData(boolean force) {
        refuseIfBusy(force);
        closeSessionsAndChannels();
        wipeNetworkRows();
        peerListService.incrementVersion();
        log.info("Nexus network data reset complete.");
    }

    /**
     * Network reset plus admin account and login throttle, so setup is required.
     */
    @Transactional
    public void factoryReset(boolean force) {
        refuseIfBusy(force);
        closeSessionsAndChannels();
        wipeNetworkRows();
        peerListService.incrementVersion();
        nexusService.clearAdminAccount();
        loginFailureThrottle.clearAll();
        log.info("Nexus factory reset complete.");
    }

    private void wipeNetworkRows() {
        fileTransferService.deleteAll();
        directoryTransferService.deleteAll();
        batchTransferService.deleteAll();
        transferLogService.deleteAll();
        pendingRenameService.deleteAll();
        deviceService.deleteAll();
    }

    private void closeSessionsAndChannels() {
        deviceWebSocketHandler.closeAllSessions();
        nexusUiWebSocketHandler.closeAllSessions();
        receiveFileChannels.closeAll();
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
}
