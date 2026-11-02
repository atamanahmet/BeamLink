package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.domain.BatchTransfer;
import com.atamanahmet.beamlink.peer.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferStartupJob implements ApplicationRunner {

    private final FileTransferService fileTransferService;
    private final DeviceService deviceService;
    private final DeviceConfig deviceConfig;
    private final DirectoryTransferService directoryTransferService;
    private final BatchTransferService batchTransferService;
    private final GroupTransferAsyncSender groupTransferAsyncSender;
    private final TransferSideResolver sideResolver;
    private final TransferManagementService transferManagementService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        UUID deviceId;
        try {
            deviceId = deviceService.getDeviceId();
        } catch (Exception e) {
            log.debug("Device not registered yet, skipping transfer resume check");
            return;
        }

        if (deviceId == null) {
            log.debug("Device ID not available yet, skipping transfer resume check");
            return;
        }

        pauseActiveReceivingTransfers();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            pauseInterruptedStandaloneTransfers();
            handleInterruptedGroupTransfers(deviceId);
        });
    }

    /**
     * Pauses ACTIVE receive rows via pauseSingle so channels flush and close.
     */
    private void pauseActiveReceivingTransfers() {
        List<FileTransfer> active = fileTransferService.findByStatus(TransferStatus.ACTIVE);
        int paused = 0;
        for (FileTransfer ft : active) {
            PauseOwner side = sideResolver.resolve(
                    ft.getSourceDeviceId(), ft.getTargetDeviceId(), ft.getTargetIp());
            if (side != PauseOwner.TARGET) {
                continue;
            }
            try {
                transferManagementService.pauseSingle(ft.getTransferId());
                paused++;
                log.info("Paused active receive on startup: {} ({})",
                        ft.getFileName(), ft.getTransferId());
            } catch (Exception e) {
                log.warn("Startup receive pause failed for {}: {}",
                        ft.getTransferId(), e.getMessage());
            }
        }
        if (paused > 0) {
            log.info("Paused {} active receive transfer(s) on startup", paused);
        }
    }

    /**
     * Standalone ACTIVE or PENDING become PAUSED owned by this side.
     */
    private void pauseInterruptedStandaloneTransfers() {
        List<FileTransfer> interrupted = new ArrayList<>();
        interrupted.addAll(fileTransferService.findByStatus(TransferStatus.ACTIVE));
        interrupted.addAll(fileTransferService.findByStatus(TransferStatus.PENDING));

        List<FileTransfer> toPause = interrupted.stream()
                .filter(t -> t.getDirectoryTransferId() == null && t.getBatchTransferId() == null)
                .toList();

        if (toPause.isEmpty()) {
            log.debug("No interrupted standalone transfers found on startup");
            return;
        }

        toPause.forEach(this::pauseOwnedByThisSide);
        fileTransferService.saveAll(toPause);
        log.info("Paused {} interrupted standalone transfer(s) on startup", toPause.size());
    }

    private void handleInterruptedGroupTransfers(UUID deviceId) {
        handleInterruptedDirectoryTransfers(deviceId);
        handleInterruptedBatchTransfers(deviceId);
    }

    private void handleInterruptedDirectoryTransfers(UUID deviceId) {
        List<DirectoryTransfer> interrupted = directoryTransferService.findAll()
                .stream()
                .filter(dt -> deviceId.equals(dt.getSourceDeviceId()))
                .filter(dt -> dt.getStatus() == GroupTransferStatus.ACTIVE)
                .toList();

        if (interrupted.isEmpty()) return;

        for (DirectoryTransfer dt : interrupted) {
            List<FileTransfer> activeChildren = fileTransferService
                    .findByDirectoryTransferIdAndStatus(dt.getDirectoryTransferId(), TransferStatus.ACTIVE);
            activeChildren.forEach(this::pauseOwnedByThisSide);
            fileTransferService.saveAll(activeChildren);

            PauseOwner side = sideResolver.resolve(
                    dt.getSourceDeviceId(), dt.getTargetDeviceId(), dt.getTargetIp());
            if (side == null) {
                throw new IllegalArgumentException("pausedBy required");
            }
            dt.setPausedBy(side);
            List<FileTransfer> children = fileTransferService
                    .findByDirectoryTransferId(dt.getDirectoryTransferId());
            dt.setStatus(GroupStatusRollup.fromChildren(
                    children.stream().map(FileTransfer::getStatus).toList()));
            directoryTransferService.save(dt);
            log.info("Paused interrupted directory transfer on startup: {} ({})",
                    dt.getDirectoryName(), dt.getDirectoryTransferId());

            if (deviceConfig.isAutoResumeGroupTransfers()) {
                GroupTransferContext ctx = new GroupTransferContext(
                        dt.getDirectoryTransferId(),
                        dt.getTargetIp(),
                        dt.getTargetPort(),
                        directoryTransferService
                );
                directoryTransferService.markActive(dt.getDirectoryTransferId());
                groupTransferAsyncSender.sendAsync(ctx);
                log.info("Auto-resumed directory transfer: {}", dt.getDirectoryTransferId());
            }
        }
    }

    private void handleInterruptedBatchTransfers(UUID deviceId) {
        List<BatchTransfer> interrupted = batchTransferService.findAll()
                .stream()
                .filter(bt -> deviceId.equals(bt.getSourceDeviceId()))
                .filter(bt -> bt.getStatus() == GroupTransferStatus.ACTIVE)
                .toList();

        if (interrupted.isEmpty()) return;

        for (BatchTransfer bt : interrupted) {
            List<FileTransfer> activeChildren = fileTransferService
                    .findByBatchTransferIdAndStatus(bt.getBatchTransferId(), TransferStatus.ACTIVE);
            activeChildren.forEach(this::pauseOwnedByThisSide);
            fileTransferService.saveAll(activeChildren);

            PauseOwner side = sideResolver.resolve(
                    bt.getSourceDeviceId(), bt.getTargetDeviceId(), bt.getTargetIp());
            if (side == null) {
                throw new IllegalArgumentException("pausedBy required");
            }
            bt.setPausedBy(side);
            List<FileTransfer> children = fileTransferService
                    .findByBatchTransferId(bt.getBatchTransferId());
            bt.setStatus(GroupStatusRollup.fromChildren(
                    children.stream().map(FileTransfer::getStatus).toList()));
            batchTransferService.save(bt);
            log.info("Paused interrupted batch transfer on startup: {}", bt.getBatchTransferId());

            if (deviceConfig.isAutoResumeGroupTransfers()) {
                GroupTransferContext ctx = new GroupTransferContext(
                        bt.getBatchTransferId(),
                        bt.getTargetIp(),
                        bt.getTargetPort(),
                        batchTransferService
                );
                batchTransferService.markActive(bt.getBatchTransferId());
                groupTransferAsyncSender.sendAsync(ctx);
                log.info("Auto-resumed batch transfer: {}", bt.getBatchTransferId());
            }
        }
    }

    /**
     * Marks one row PAUSED owned by this app side.
     */
    private void pauseOwnedByThisSide(FileTransfer ft) {
        PauseOwner side = sideResolver.resolve(
                ft.getSourceDeviceId(), ft.getTargetDeviceId(), ft.getTargetIp());
        if (side == null) {
            throw new IllegalArgumentException("pausedBy required");
        }
        ft.setStatus(TransferStatus.PAUSED);
        ft.setPausedBy(side);
    }
}
