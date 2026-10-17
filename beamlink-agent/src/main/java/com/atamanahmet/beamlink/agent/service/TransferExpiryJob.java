package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferExpiryJob {

    private final AgentConfig agentConfig;
    private final FileTransferRepository transferRepository;
    private final StorageService storageService;
    private final ChunkReceiverService chunkReceiverService;

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void expireStaleTransfers() {
        Instant now = Instant.now();
        List<FileTransfer> stale = new ArrayList<>();
        stale.addAll(transferRepository.findByStatusAndExpiresAtBefore(TransferStatus.ACTIVE, now));
        stale.addAll(transferRepository.findByStatusAndExpiresAtBefore(TransferStatus.PAUSED, now));
        if (!stale.isEmpty()) {
            for(FileTransfer transfer : stale) {
                transfer.setStatus(TransferStatus.EXPIRED);
                chunkReceiverService.onReceiveExpired(transfer.getTransferId());
                storageService.deletePartialFile(transfer.getFileName());
                log.info("Transfer expired: {} ({})", transfer.getFileName(), transfer.getTransferId());
            }

            transferRepository.saveAll(stale);
        }
    }
}