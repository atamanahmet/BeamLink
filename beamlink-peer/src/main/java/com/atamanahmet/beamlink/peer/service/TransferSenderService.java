package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.peer.dto.InitiateTransferResponse;
import com.atamanahmet.beamlink.peer.exception.FileTransferException;
import com.atamanahmet.beamlink.peer.exception.TransferProtocolException;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.util.PathNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSenderService {

    private final FileTransferService fileTransferService;
    private final DeviceConfig deviceConfig;
    private final TransferHttpClient transferHttpClient;
    private final DeviceService deviceService;

    /**
     * Registers on target first, rollback if it fails
     */
    public InitiateTransferResponse initiate(InitiateTransferRequest request) {
        String cleanedPath = PathNormalizer.normalize(request.getFilePath());
        Path filePath = Paths.get(cleanedPath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileTransferException("File not found: " + cleanedPath, null);
        }

        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new FileTransferException("Cannot read file size: " + cleanedPath, e);
        }

        UUID transferId = UUID.randomUUID();
        String fileName = filePath.getFileName().toString();

        FileTransfer transfer = FileTransfer.initiate(
                transferId,
                deviceService.getDeviceId(),
                request.getTargetDeviceId(),
                fileName,
                cleanedPath,
                fileSize
        );
        transfer.setTargetIp(request.getTargetIp());
        transfer.setTargetPort(request.getTargetPort());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setExpiresAt(Instant.now().plusSeconds(deviceConfig.getTransferExpiryHours() * 3600L));
        fileTransferService.save(transfer);

        try {
            transferHttpClient.registerTransfer(
                    request, transferId, deviceService.getDeviceId(), fileName, fileSize);
        } catch (TransferProtocolException e) {
            if (RegisterRejectRule.keepFailed(e.getCode())) {
                transfer.setStatus(TransferStatus.FAILED);
                transfer.setFailureReason(e.getMessage());
                fileTransferService.save(transfer);
            } else {
                fileTransferService.delete(transfer);
            }
            throw e;
        } catch (FileTransferException e) {
            fileTransferService.delete(transfer);
            throw e;
        }

        transfer.setStatus(TransferStatus.ACTIVE);
        fileTransferService.save(transfer);
        log.info("Transfer initiated: {} → {} ({})", fileName, request.getTargetDeviceId(), transferId);

        return new InitiateTransferResponse(transferId);
    }

    public void resume(UUID transferId) {
        FileTransfer transfer = fileTransferService.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));

        if (transfer.getStatus() != TransferStatus.PAUSED) {
            throw new FileTransferException("Transfer is not paused, current status: " + transfer.getStatus(), null);
        }

        if (transfer.getTargetIp() == null || transfer.getTargetIp().isBlank()) {
            throw new FileTransferException("Transfer has no target IP stored, cannot resume", null);
        }

        String cleanedPath = PathNormalizer.normalize(transfer.getFilePath());
        if (!Files.exists(Paths.get(cleanedPath))) {
            throw new FileTransferException("Source file no longer exists: " + cleanedPath, null);
        }

        long targetOffset;
        try {
            targetOffset = transferHttpClient.resolveStartOffset(
                    transfer.getTargetIp(), transfer.getTargetPort(), transferId,
                    transfer.getConfirmedOffset());
        } catch (TransferProtocolException e) {
            if (SessionLostRule.isLost(e.getCode())) {
                markFailed(transferId, SessionLostRule.MESSAGE);
            }
            throw e;
        }

        if (targetOffset != transfer.getConfirmedOffset()) {
            log.info("Correcting offset for {} from {} to {} (target state)",
                    transferId, transfer.getConfirmedOffset(), targetOffset);
            transfer.setConfirmedOffset(targetOffset);
        }

        transfer.setPausedBy(null);
        transfer.setStatus(TransferStatus.ACTIVE);
        fileTransferService.save(transfer);
    }

    public FileTransfer getTransfer(UUID transferId) {
        return fileTransferService.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));
    }

    public TransferStatus getStatus(UUID transferId) {
        return fileTransferService.findStatusByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));
    }

    public List<FileTransfer> getAll() {
        return fileTransferService.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void cancel(UUID transferId) {
        fileTransferService.findByTransferId(transferId).ifPresent(transfer -> {
            if (transfer.getStatus() == TransferStatus.ACTIVE
                    || transfer.getStatus() == TransferStatus.PAUSED) {
                transfer.setStatus(TransferStatus.CANCELLED);
                fileTransferService.save(transfer);
                log.info("Transfer cancelled: {}", transferId);
            }
        });
    }

    /** Only terminal state transfers can be deleted. */
    @Transactional
    public void delete(UUID transferId) {
        fileTransferService.findByTransferId(transferId).ifPresent(transfer -> {
            TransferStatus s = transfer.getStatus();
            if (s == TransferStatus.COMPLETED
                    || s == TransferStatus.FAILED
                    || s == TransferStatus.CANCELLED
                    || s == TransferStatus.EXPIRED) {
                fileTransferService.delete(transfer);
                log.info("Transfer deleted by user: {}", transferId);
            }
        });
    }

    @Transactional
    public void markFailed(UUID transferId, String reason) {
        fileTransferService.findByTransferId(transferId).ifPresent(transfer -> {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(reason);
            fileTransferService.save(transfer);
            if (transfer.getTargetIp() != null && transfer.getTargetPort() != null) {
                transferHttpClient.notifyFailToPeer(
                        transfer.getTargetIp(), transfer.getTargetPort(), transferId, reason);
            }
        });
    }

    /**
     * Peer fail-notify: mark local sender row FAILED without notifying back.
     */
    @Transactional
    public void applyFailNotify(UUID transferId, String reason) {
        fileTransferService.findByTransferId(transferId).ifPresent(transfer -> {
            TransferStatus s = transfer.getStatus();
            if (s == TransferStatus.ACTIVE
                    || s == TransferStatus.PAUSED
                    || s == TransferStatus.PENDING) {
                transfer.setStatus(TransferStatus.FAILED);
                transfer.setFailureReason(reason);
                fileTransferService.save(transfer);
                log.info("Transfer failed via peer notify: {} ({})", transferId, reason);
            }
        });
    }
}