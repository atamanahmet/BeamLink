package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.agent.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.agent.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.agent.dto.TransferLogDTO;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkReceiverService {

    private static final int BUFFER_SIZE = 8192;

    private final FileTransferRepository fileTransferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final BatchTransferRepository batchTransferRepository;
    private final TransferLogService transferLogService;
    private final AgentService agentService;
    private final StorageService storageService;

    /**
     * Called by target agent when source initiates a transfer.
     * Creates the FileTransfer record and prepares the partial file on disk.
     */
    @Transactional
    public void prepareReceive(FileTransfer transfer) {
        if (transfer.getFileSize() <= 0) {
            throw new FileTransferException("Invalid file size: " + transfer.getFileSize(), null);
        }

        Path partialFile = storageService.resolvePartialPath(transfer.getFileName());
        storageService.allocatePartialFile(partialFile, transfer.getFileSize());
        fileTransferRepository.save(transfer);
        log.info("Prepared to receive: {} ({} bytes)", transfer.getFileName(), transfer.getFileSize());
    }

     /**
     * Called by target when source registers a directory transfer.
     * Creates DirectoryTransfer record, empty dirs on disk,
     * allocates partial files, saves all in one batch.
     */
    @Transactional
    public void prepareReceiveDirectory(ReceiveDirectoryRequest request) {
        DirectoryTransfer dt = DirectoryTransfer.initiate(
                request.getDirectoryTransferId(),
                request.getDispatchId(),
                request.getSourceAgentId(),
                null,
                null,
                0,
                request.getDirectoryName(),
                null,
                request.getTotalFiles(),
                request.getTotalSize(),
                request.getEmptyDirectories() != null
                        ? request.getEmptyDirectories()
                        : Collections.emptyList()
        );
        dt.setStatus(GroupTransferStatus.ACTIVE);
        directoryTransferRepository.save(dt);

        Path dirRoot = storageService.getUploadsDir().resolve(request.getDirectoryName());
        for (String emptyDir : dt.getEmptyDirectories()) {
            storageService.createDirectory(dirRoot.resolve(emptyDir));
        }

        List<FileTransfer> fileTransfers = new ArrayList<>();
        for (ReceiveDirectoryRequest.FileEntry entry : request.getFiles()) {
            if (entry.getFileSize() <= 0) {
                throw new FileTransferException("Invalid file size for: " + entry.getFileName(), null);
            }

            FileTransfer ft = FileTransfer.initiate(
                    entry.getTransferId(),
                    request.getSourceAgentId(),
                    null,
                    entry.getFileName(),
                    null,
                    entry.getFileSize()
            );
            ft.setDirectoryTransferId(request.getDirectoryTransferId());
            ft.setRelativePath(entry.getRelativePath());
            ft.setDirectoryName(request.getDirectoryName());
            ft.setStatus(TransferStatus.ACTIVE);

            Path partialFile = storageService.resolvePartialPath(entry.getFileName());
            storageService.allocatePartialFile(partialFile, entry.getFileSize());
            fileTransfers.add(ft);
        }

        fileTransferRepository.saveAll(fileTransfers);
        log.info("Prepared to receive directory: {} ({} files)", request.getDirectoryName(), fileTransfers.size());
    }

    /**
     * Called by target when source registers a batch transfer.
     * Creates BatchTransfer record, allocates partial files, saves all in one batch.
     */
    @Transactional
    public void prepareReceiveBatch(ReceiveBatchRequest request) {
        BatchTransfer bt = BatchTransfer.initiate(
                request.getBatchTransferId(),
                request.getDispatchId(),
                request.getSourceAgentId(),
                null,
                null,
                0,
                request.getTotalFiles(),
                request.getTotalSize()
        );
        bt.setStatus(GroupTransferStatus.ACTIVE);
        batchTransferRepository.save(bt);

        List<FileTransfer> fileTransfers = new ArrayList<>();
        for (ReceiveBatchRequest.FileEntry entry : request.getFiles()) {
            if (entry.getFileSize() <= 0) {
                throw new FileTransferException("Invalid file size for: " + entry.getFileName(), null);
            }

            FileTransfer ft = FileTransfer.initiate(
                    entry.getTransferId(),
                    request.getSourceAgentId(),
                    null,
                    entry.getFileName(),
                    null,
                    entry.getFileSize()
            );
            ft.setBatchTransferId(request.getBatchTransferId());
            ft.setStatus(TransferStatus.ACTIVE);

            Path partialFile = storageService.resolvePartialPath(entry.getFileName());
            storageService.allocatePartialFile(partialFile, entry.getFileSize());
            fileTransfers.add(ft);
        }

        fileTransferRepository.saveAll(fileTransfers);
        log.info("Prepared to receive batch: {} ({} files)", request.getBatchTransferId(), fileTransfers.size());
    }

    @Transactional
    public ChunkAckResponse receiveChunk(UUID transferId, long offset, InputStream chunkStream) {
        FileTransfer transfer = fileTransferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));

        if (transfer.getStatus() != TransferStatus.ACTIVE) {
            throw new FileTransferException("Transfer is not active: " + transfer.getStatus(), null);
        }

        if (offset != transfer.getConfirmedOffset()) {
            throw new FileTransferException(
                    "Unexpected offset. Expected: " + transfer.getConfirmedOffset() + " got: " + offset, null);
        }

        Path partialFile = storageService.resolvePartialPath(transfer.getFileName());
        long bytesWritten = storageService.writeChunk(partialFile, offset, chunkStream);
        long newOffset = offset + bytesWritten;
        Instant now = Instant.now();

        if (transfer.getLastChunkAt() != null) {
            long delta = now.toEpochMilli() - transfer.getLastChunkAt().toEpochMilli();
            transfer.setActiveTransferMs(transfer.getActiveTransferMs() + delta);
        }

        transfer.setConfirmedOffset(newOffset);
        transfer.setLastChunkAt(now);

        boolean complete = newOffset >= transfer.getFileSize();
        if (complete) {
            transfer.setStatus(TransferStatus.COMPLETED);
            storageService.moveToFinalLocation(transfer);
            logCompletedTransfer(transfer);
            log.info("Transfer completed: {}", transfer.getFileName());
            checkAndCompleteParent(transfer);
        }

        fileTransferRepository.save(transfer);
        return new ChunkAckResponse(newOffset, complete);
    }

    private void logCompletedTransfer(FileTransfer transfer) {
        try {
            double speedMbps = transfer.getActiveTransferMs() > 0
                    ? (double) transfer.getFileSize() * 8.0 / transfer.getActiveTransferMs() / 1000.0
                    : 0.0;

            TransferLogDTO dto = TransferLogDTO.builder()
                    .id(UUID.randomUUID())
                    .filename(transfer.getFileName())
                    .fileSize(transfer.getFileSize())
                    .fromAgentId(transfer.getSourceAgentId())
                    .toAgentId(agentService.getAgentId())
                    .toAgentName(agentService.getAgentName())
                    .averageSpeedMbps(speedMbps)
                    .timestamp(Instant.now())
                    .build();

            transferLogService.logTransfer(dto);
        } catch (Exception e) {
            log.warn("Failed to log completed transfer, file was saved successfully", e);
        }

    }

    private void checkAndCompleteParent(FileTransfer transfer) {
        if (transfer.getBatchTransferId() != null) {
            UUID batchId = transfer.getBatchTransferId();
            List<FileTransfer> siblings = fileTransferRepository.findByBatchTransferId(batchId);

            long completed = siblings.stream().filter(ft -> ft.getStatus() == TransferStatus.COMPLETED).count();
            long failed = siblings.stream().filter(ft ->
                    ft.getStatus() == TransferStatus.FAILED || ft.getStatus() == TransferStatus.CANCELLED).count();

            if (completed + failed < siblings.size()) return;

            BatchTransfer bt = batchTransferRepository.findById(batchId).orElse(null);
            if (bt == null) return;

            if (failed == 0) {
                bt.setStatus(GroupTransferStatus.COMPLETED);
                bt.setCompletedAt(Instant.now());
                log.info("Batch completed on receiver: {}", batchId);
            } else if (completed > 0) {
                bt.setStatus(GroupTransferStatus.PARTIAL);
                log.info("Batch partial on receiver: {}", batchId);
            } else {
                bt.setStatus(GroupTransferStatus.FAILED);
                log.warn("Batch failed on receiver: {}", batchId);
            }

            batchTransferRepository.save(bt);
        }

        if (transfer.getDirectoryTransferId() != null) {
            UUID dirId = transfer.getDirectoryTransferId();
            List<FileTransfer> siblings = fileTransferRepository.findByDirectoryTransferId(dirId);

            long completed = siblings.stream().filter(ft -> ft.getStatus() == TransferStatus.COMPLETED).count();
            long failed = siblings.stream().filter(ft ->
                    ft.getStatus() == TransferStatus.FAILED || ft.getStatus() == TransferStatus.CANCELLED).count();

            if (completed + failed < siblings.size()) return;

            DirectoryTransfer dt = directoryTransferRepository.findById(dirId).orElse(null);
            if (dt == null) return;

            if (failed == 0) {
                dt.setStatus(GroupTransferStatus.COMPLETED);
                dt.setCompletedAt(Instant.now());
                log.info("Directory transfer completed on receiver: {}", dirId);
            } else if (completed > 0) {
                dt.setStatus(GroupTransferStatus.PARTIAL);
                log.info("Directory transfer partial on receiver: {}", dirId);
            } else {
                dt.setStatus(GroupTransferStatus.FAILED);
                log.warn("Directory transfer failed on receiver: {}", dirId);
            }

            directoryTransferRepository.save(dt);
        }
    }

    public long getConfirmedOffset(UUID transferId) {
        return fileTransferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null))
                .getConfirmedOffset();
    }

    @Transactional
    public void cancelReceive(UUID transferId) {
        fileTransferRepository.findByTransferId(transferId).ifPresent(ft -> {
            if (ft.getStatus() == TransferStatus.ACTIVE
                    || ft.getStatus() == TransferStatus.PAUSED) {
                ft.setStatus(TransferStatus.CANCELLED);
                fileTransferRepository.save(ft);
                storageService.deletePartialFile(ft.getFileName());
            }
        });
    }
}