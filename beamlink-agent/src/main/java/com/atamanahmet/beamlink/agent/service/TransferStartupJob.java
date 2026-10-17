package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
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

    private final FileTransferRepository transferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final BatchTransferRepository batchTransferRepository;
    private final AgentService agentService;
    private final AgentConfig agentConfig;
    private final DirectoryTransferService directoryTransferService;
    private final BatchTransferService batchTransferService;
    private final GroupTransferAsyncSender groupTransferAsyncSender;
    private final TransferSideResolver sideResolver;
    private final TransferManagementService transferManagementService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        UUID agentId;
        try {
            agentId = agentService.getAgentId();
        } catch (Exception e) {
            log.debug("Agent not registered yet, skipping transfer resume check");
            return;
        }

        if (agentId == null) {
            log.debug("Agent ID not available yet, skipping transfer resume check");
            return;
        }

        pauseActiveReceivingTransfers();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            pauseInterruptedStandaloneTransfers();
            handleInterruptedGroupTransfers(agentId);
        });
    }

    /**
     * Pauses ACTIVE receive rows via pauseSingle so channels flush and close.
     */
    private void pauseActiveReceivingTransfers() {
        List<FileTransfer> active = transferRepository.findByStatus(TransferStatus.ACTIVE);
        int paused = 0;
        for (FileTransfer ft : active) {
            PauseOwner side = sideResolver.resolve(
                    ft.getSourceAgentId(), ft.getTargetAgentId(), ft.getTargetIp());
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
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.ACTIVE));
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.PENDING));

        List<FileTransfer> toPause = interrupted.stream()
                .filter(t -> t.getDirectoryTransferId() == null && t.getBatchTransferId() == null)
                .toList();

        if (toPause.isEmpty()) {
            log.debug("No interrupted standalone transfers found on startup");
            return;
        }

        toPause.forEach(this::pauseOwnedByThisSide);
        transferRepository.saveAll(toPause);
        log.info("Paused {} interrupted standalone transfer(s) on startup", toPause.size());
    }

    private void handleInterruptedGroupTransfers(UUID agentId) {
        handleInterruptedDirectoryTransfers(agentId);
        handleInterruptedBatchTransfers(agentId);
    }

    private void handleInterruptedDirectoryTransfers(UUID agentId) {
        List<DirectoryTransfer> interrupted = directoryTransferRepository.findAll()
                .stream()
                .filter(dt -> agentId.equals(dt.getSourceAgentId()))
                .filter(dt -> dt.getStatus() == GroupTransferStatus.ACTIVE)
                .toList();

        if (interrupted.isEmpty()) return;

        for (DirectoryTransfer dt : interrupted) {
            List<FileTransfer> activeChildren = transferRepository
                    .findByDirectoryTransferIdAndStatus(dt.getDirectoryTransferId(), TransferStatus.ACTIVE);
            activeChildren.forEach(this::pauseOwnedByThisSide);
            transferRepository.saveAll(activeChildren);

            PauseOwner side = sideResolver.resolve(
                    dt.getSourceAgentId(), dt.getTargetAgentId(), dt.getTargetIp());
            dt.setPausedBy(side);
            List<FileTransfer> children = transferRepository
                    .findByDirectoryTransferId(dt.getDirectoryTransferId());
            dt.setStatus(GroupStatusRollup.fromChildren(
                    children.stream().map(FileTransfer::getStatus).toList()));
            directoryTransferRepository.save(dt);
            log.info("Paused interrupted directory transfer on startup: {} ({})",
                    dt.getDirectoryName(), dt.getDirectoryTransferId());

            if (agentConfig.isAutoResumeGroupTransfers()) {
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

    private void handleInterruptedBatchTransfers(UUID agentId) {
        List<BatchTransfer> interrupted = batchTransferRepository.findAll()
                .stream()
                .filter(bt -> agentId.equals(bt.getSourceAgentId()))
                .filter(bt -> bt.getStatus() == GroupTransferStatus.ACTIVE)
                .toList();

        if (interrupted.isEmpty()) return;

        for (BatchTransfer bt : interrupted) {
            List<FileTransfer> activeChildren = transferRepository
                    .findByBatchTransferIdAndStatus(bt.getBatchTransferId(), TransferStatus.ACTIVE);
            activeChildren.forEach(this::pauseOwnedByThisSide);
            transferRepository.saveAll(activeChildren);

            PauseOwner side = sideResolver.resolve(
                    bt.getSourceAgentId(), bt.getTargetAgentId(), bt.getTargetIp());
            bt.setPausedBy(side);
            List<FileTransfer> children = transferRepository
                    .findByBatchTransferId(bt.getBatchTransferId());
            bt.setStatus(GroupStatusRollup.fromChildren(
                    children.stream().map(FileTransfer::getStatus).toList()));
            batchTransferRepository.save(bt);
            log.info("Paused interrupted batch transfer on startup: {}", bt.getBatchTransferId());

            if (agentConfig.isAutoResumeGroupTransfers()) {
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
                ft.getSourceAgentId(), ft.getTargetAgentId(), ft.getTargetIp());
        ft.setStatus(TransferStatus.PAUSED);
        ft.setPausedBy(side);
    }
}
