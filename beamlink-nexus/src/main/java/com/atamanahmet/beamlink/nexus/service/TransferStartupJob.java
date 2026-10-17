package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TransferStartupJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TransferStartupJob.class);

    private final FileTransferRepository transferRepository;
    private final TransferSideResolver sideResolver;
    private final TransferManagementService transferManagementService;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        pauseActiveReceivingTransfers();
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> pauseInterruptedTransfers());
    }

    /**
     * Pauses ACTIVE receive rows via pauseSingle so channels flush and close.
     */
    private void pauseActiveReceivingTransfers() {
        List<FileTransfer> active = transferRepository.findByStatus(TransferStatus.ACTIVE);
        int paused = 0;
        for (FileTransfer t : active) {
            PauseOwner side = sideResolver.resolve(
                    t.getSourceAgentId(), t.getTargetAgentId(), t.getTargetIp());
            if (side != PauseOwner.TARGET) {
                continue;
            }
            try {
                transferManagementService.pauseSingle(t.getTransferId());
                paused++;
                log.info("Paused active receive on startup: {} ({})",
                        t.getFileName(), t.getTransferId());
            } catch (Exception e) {
                log.warn("Startup receive pause failed for {}: {}",
                        t.getTransferId(), e.getMessage());
            }
        }
        if (paused > 0) {
            log.info("Paused {} active receive transfer(s) on startup", paused);
        }
    }

    /**
     * Remaining ACTIVE or PENDING become PAUSED owned by this side.
     */
    private void pauseInterruptedTransfers() {
        List<FileTransfer> interrupted = new ArrayList<>();
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.ACTIVE));
        interrupted.addAll(transferRepository.findByStatus(TransferStatus.PENDING));

        if (interrupted.isEmpty()) {
            log.debug("No interrupted transfers found on startup");
            return;
        }

        for (FileTransfer t : interrupted) {
            PauseOwner side = sideResolver.resolve(
                    t.getSourceAgentId(), t.getTargetAgentId(), t.getTargetIp());
            t.setStatus(TransferStatus.PAUSED);
            t.setPausedBy(side);
            log.info("Paused interrupted transfer on startup: {} ({})",
                    t.getFileName(), t.getTransferId());
        }

        transferRepository.saveAll(interrupted);
        log.info("Paused {} interrupted transfer(s) on startup", interrupted.size());
    }
}
