package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.http.TransferHttpClient;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.atamanahmet.beamlink.agent.util.PathNormalizer;
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

    private static final int CHUNK_SIZE = 8_388_608; // 8MB

    private final FileTransferRepository transferRepository;
    private final AgentConfig agentConfig;
    private final TransferHttpClient transferHttpClient;
    private final AgentService agentService;

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
                agentService.getAgentId(),
                request.getTargetAgentId(),
                fileName,
                cleanedPath,
                fileSize
        );
        transfer.setTargetIp(request.getTargetIp());
        transfer.setTargetPort(request.getTargetPort());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setExpiresAt(Instant.now().plusSeconds(agentConfig.getTransferExpiryHours() * 3600L));
        transferRepository.save(transfer);

        try {
            transferHttpClient.registerTransfer(request, transferId, agentService.getAgentId(), fileName, fileSize);
        } catch (FileTransferException e) {
            transferRepository.delete(transfer);
            throw e;
        }

        transfer.setStatus(TransferStatus.ACTIVE);
        transferRepository.save(transfer);
        log.info("Transfer initiated: {} → {} ({})", fileName, request.getTargetAgentId(), transferId);

        return new InitiateTransferResponse(transferId);
    }

    public void resume(UUID transferId) {
        FileTransfer transfer = transferRepository.findByTransferId(transferId)
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

        /* Sync offset with target before resuming to avoid re-sending confirmed chunks */
        long targetOffset = transferHttpClient.queryConfirmedOffset(
                transfer.getTargetIp(), transfer.getTargetPort(), transferId);

        if (targetOffset != transfer.getConfirmedOffset()) {
            log.info("Correcting offset for {} from {} to {} (target state)",
                    transferId, transfer.getConfirmedOffset(), targetOffset);
            transfer.setConfirmedOffset(targetOffset);
        }

        transfer.setStatus(TransferStatus.ACTIVE);
        transferRepository.save(transfer);
    }

    public FileTransfer getTransfer(UUID transferId) {
        return transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));
    }

    public TransferStatus getStatus(UUID transferId) {
        return transferRepository.findStatusByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));
    }

    public List<FileTransfer> getAll() {
        return transferRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void cancel(UUID transferId) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            if (transfer.getStatus() == TransferStatus.ACTIVE
                    || transfer.getStatus() == TransferStatus.PAUSED) {
                transfer.setStatus(TransferStatus.CANCELLED);
                transferRepository.save(transfer);
                log.info("Transfer cancelled: {}", transferId);
            }
        });
    }

    /** Only terminal state transfers can be deleted. */
    @Transactional
    public void delete(UUID transferId) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            TransferStatus s = transfer.getStatus();
            if (s == TransferStatus.COMPLETED
                    || s == TransferStatus.FAILED
                    || s == TransferStatus.CANCELLED
                    || s == TransferStatus.EXPIRED) {
                transferRepository.delete(transfer);
                log.info("Transfer deleted by user: {}", transferId);
            }
        });
    }

    @Transactional
    public void markFailed(UUID transferId, String reason) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(reason);
            transferRepository.save(transfer);
        });
    }
}