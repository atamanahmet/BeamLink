package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
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

    private final DeviceConfig deviceConfig;
    private final FileTransferService fileTransferService;
    private final StorageService storageService;
    private final ChunkReceiverService chunkReceiverService;

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void expireStaleTransfers() {
        Instant now = Instant.now();
        List<FileTransfer> stale = new ArrayList<>();
        stale.addAll(fileTransferService.findByStatusAndExpiresAtBefore(TransferStatus.ACTIVE, now));
        stale.addAll(fileTransferService.findByStatusAndExpiresAtBefore(TransferStatus.PAUSED, now));
        if (!stale.isEmpty()) {
            for(FileTransfer transfer : stale) {
                transfer.setStatus(TransferStatus.EXPIRED);
                chunkReceiverService.onReceiveExpired(transfer.getTransferId());
                storageService.deletePartialFile(transfer);
                log.info("Transfer expired: {} ({})", transfer.getFileName(), transfer.getTransferId());
            }

            fileTransferService.saveAll(stale);
        }
    }
}