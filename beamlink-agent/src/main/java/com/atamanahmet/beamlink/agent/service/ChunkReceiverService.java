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
import com.atamanahmet.beamlink.agent.exception.TransferProtocolException;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkReceiverService {

    private static final long RECEIVER_SAVE_INTERVAL_MS = 1000L;

    private final FileTransferRepository fileTransferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final BatchTransferRepository batchTransferRepository;
    private final TransferLogService transferLogService;
    private final AgentService agentService;
    private final StorageService storageService;
    private final ReceiveFileChannels receiveFileChannels;
    private final ReceiveStallTracker receiveStallTracker;

    private final ConcurrentHashMap<UUID, Long> liveConfirmedOffset = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastReceiverSaveAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, TempP0Receiver> tempP0ByTransfer = new ConcurrentHashMap<>();

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
        receiveStallTracker.touch(transfer.getTransferId());
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
        for (FileTransfer ft : fileTransfers) {
            receiveStallTracker.touch(ft.getTransferId());
        }
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
        for (FileTransfer ft : fileTransfers) {
            receiveStallTracker.touch(ft.getTransferId());
        }
        log.info("Prepared to receive batch: {} ({} files)", request.getBatchTransferId(), fileTransfers.size());
    }

    /**
     * Writes one chunk. DB progress saved at most once per second, always on complete.
     */
    public ChunkAckResponse receiveChunk(
            UUID transferId, long offset, long length, InputStream chunkStream) {
        FileTransfer transfer = fileTransferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));

        if (transfer.getStatus() == TransferStatus.PAUSED) {
            throw new TransferProtocolException(
                    "TRANSFER_PAUSED",
                    "Transfer is paused",
                    org.springframework.http.HttpStatus.CONFLICT,
                    transfer.getPausedBy());
        }

        if (transfer.getStatus() != TransferStatus.ACTIVE) {
            throw new FileTransferException("Transfer is not active: " + transfer.getStatus(), null);
        }

        long expected = liveConfirmedOffset.getOrDefault(transferId, transfer.getConfirmedOffset());
        ReceiveOffsetRule.Decision decision = ReceiveOffsetRule.decide(offset, length, expected);
        if (decision == ReceiveOffsetRule.Decision.DUPLICATE) {
            try {
                chunkStream.transferTo(OutputStream.nullOutputStream());
            } catch (IOException e) {
                throw new FileTransferException("Failed to drain duplicate chunk", e);
            }
            return new ChunkAckResponse(expected, expected >= transfer.getFileSize());
        }
        if (decision != ReceiveOffsetRule.Decision.ACCEPT) {
            tempP0(transferId).rejectedOoo.incrementAndGet();
            throw new FileTransferException(
                    "Unexpected offset. Expected: " + expected + " got: " + offset, null);
        }

        Path partialFile = storageService.resolvePartialPath(transfer.getFileName());
        long bytesWritten;
        try {
            long writeStart = System.nanoTime();
            bytesWritten = receiveFileChannels.write(
                    transferId, partialFile, offset, chunkStream);
            tempP0(transferId).writeMs.addAndGet((System.nanoTime() - writeStart) / 1_000_000L);
        } catch (RuntimeException e) {
            receiveFileChannels.close(transferId);
            receiveStallTracker.remove(transferId);
            logTempP0Summary(transferId, "failure");
            throw e;
        }
        tempP0(transferId).accepted.incrementAndGet();
        long newOffset = offset + bytesWritten;
        Instant now = Instant.now();

        if (transfer.getLastChunkAt() != null) {
            long delta = now.toEpochMilli() - transfer.getLastChunkAt().toEpochMilli();
            transfer.setActiveTransferMs(transfer.getActiveTransferMs() + delta);
        }

        transfer.setConfirmedOffset(newOffset);
        transfer.setLastChunkAt(now);
        liveConfirmedOffset.put(transferId, newOffset);
        receiveStallTracker.touch(transferId);

        boolean complete = newOffset >= transfer.getFileSize();
        if (complete) {
            transfer.setStatus(TransferStatus.COMPLETED);
            fileTransferRepository.save(transfer);
            receiveFileChannels.close(transferId);
            storageService.moveToFinalLocation(transfer);
            logCompletedTransfer(transfer);
            log.info("Transfer completed: {}", transfer.getFileName());
            logTempP0Summary(transferId, "complete");
            liveConfirmedOffset.remove(transferId);
            lastReceiverSaveAt.remove(transferId);
            receiveStallTracker.remove(transferId);
            checkAndCompleteParent(transfer);
        } else {
            long nowMs = System.currentTimeMillis();
            Long last = lastReceiverSaveAt.get(transferId);
            if (last == null || nowMs - last >= RECEIVER_SAVE_INTERVAL_MS) {
                fileTransferRepository.save(transfer);
                lastReceiverSaveAt.put(transferId, nowMs);
            }
        }

        return new ChunkAckResponse(newOffset, complete);
    }

    /**
     * Flushes live confirmedOffset to DB and closes the receive channel.
     */
    @Transactional
    public void onReceivePaused(UUID transferId) {
        fileTransferRepository.findByTransferId(transferId).ifPresent(ft -> {
            long live = liveConfirmedOffset.getOrDefault(transferId, ft.getConfirmedOffset());
            ft.setConfirmedOffset(live);
            fileTransferRepository.save(ft);
            liveConfirmedOffset.put(transferId, live);
            lastReceiverSaveAt.remove(transferId);
        });
        receiveFileChannels.close(transferId);
        receiveStallTracker.remove(transferId);
        logTempP0Summary(transferId, "pause");
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

    @Transactional
    protected void checkAndCompleteParent(FileTransfer transfer) {
        if (transfer.getBatchTransferId() != null) {
            UUID batchId = transfer.getBatchTransferId();
            List<FileTransfer> siblings = fileTransferRepository.findByBatchTransferId(batchId);
            if (hasInProgressChild(siblings)) {
                return;
            }

            BatchTransfer bt = batchTransferRepository.findById(batchId).orElse(null);
            if (bt == null) {
                return;
            }

            GroupTransferStatus status = GroupStatusRollup.fromChildren(
                    siblings.stream().map(FileTransfer::getStatus).toList());
            bt.setStatus(status);
            if (status == GroupTransferStatus.COMPLETED) {
                bt.setCompletedAt(Instant.now());
            }
            batchTransferRepository.save(bt);
            log.info("Batch rollup on receiver: {} -> {}", batchId, status);
        }

        if (transfer.getDirectoryTransferId() != null) {
            UUID dirId = transfer.getDirectoryTransferId();
            List<FileTransfer> siblings = fileTransferRepository.findByDirectoryTransferId(dirId);
            if (hasInProgressChild(siblings)) {
                return;
            }

            DirectoryTransfer dt = directoryTransferRepository.findById(dirId).orElse(null);
            if (dt == null) {
                return;
            }

            GroupTransferStatus status = GroupStatusRollup.fromChildren(
                    siblings.stream().map(FileTransfer::getStatus).toList());
            dt.setStatus(status);
            if (status == GroupTransferStatus.COMPLETED) {
                dt.setCompletedAt(Instant.now());
            }
            directoryTransferRepository.save(dt);
            log.info("Directory rollup on receiver: {} -> {}", dirId, status);
        }
    }

    private static boolean hasInProgressChild(List<FileTransfer> siblings) {
        return siblings.stream().anyMatch(ft -> {
            TransferStatus s = ft.getStatus();
            return s == TransferStatus.ACTIVE
                    || s == TransferStatus.PAUSED
                    || s == TransferStatus.PENDING;
        });
    }

    public long getConfirmedOffset(UUID transferId) {
        Long live = liveConfirmedOffset.get(transferId);
        if (live != null) {
            return live;
        }
        return fileTransferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null))
                .getConfirmedOffset();
    }

    @Transactional
    public void cancelReceive(UUID transferId) {
        fileTransferRepository.findByTransferId(transferId).ifPresent(ft -> {
            if (ft.getStatus() == TransferStatus.ACTIVE
                    || ft.getStatus() == TransferStatus.PAUSED) {
                long live = liveConfirmedOffset.getOrDefault(transferId, ft.getConfirmedOffset());
                ft.setConfirmedOffset(live);
                ft.setStatus(TransferStatus.CANCELLED);
                fileTransferRepository.save(ft);
                receiveFileChannels.close(transferId);
                storageService.deletePartialFile(ft.getFileName());
                liveConfirmedOffset.remove(transferId);
                lastReceiverSaveAt.remove(transferId);
                receiveStallTracker.remove(transferId);
                logTempP0Summary(transferId, "cancel");
            }
        });
    }

    /**
     * Closes the receive channel before expiry deletes the partial file.
     */
    public void onReceiveExpired(UUID transferId) {
        receiveFileChannels.close(transferId);
        liveConfirmedOffset.remove(transferId);
        lastReceiverSaveAt.remove(transferId);
        receiveStallTracker.remove(transferId);
    }

    private TempP0Receiver tempP0(UUID transferId) {
        return tempP0ByTransfer.computeIfAbsent(transferId, id -> new TempP0Receiver());
    }

    /**
     * TEMP-P0 one summary line for receiver counters.
     */
    private void logTempP0Summary(UUID transferId, String event) {
        TempP0Receiver c = tempP0ByTransfer.get(transferId);
        long accepted = c == null ? 0L : c.accepted.get();
        long rejectedOoo = c == null ? 0L : c.rejectedOoo.get();
        long writeMs = c == null ? 0L : c.writeMs.get();
        log.info(
                "TEMP-P0 receiver event={} transferId={} accepted={} rejectedOoo={} writeMs={} openCount={} closeCount={}",
                event,
                transferId,
                accepted,
                rejectedOoo,
                writeMs,
                receiveFileChannels.tempP0OpenCount(transferId),
                receiveFileChannels.tempP0CloseCount(transferId));
        tempP0ByTransfer.remove(transferId);
        receiveFileChannels.clearTempP0Counts(transferId);
    }

    /**
     * TEMP-P0 per-transfer receiver counters.
     */
    private static final class TempP0Receiver {
        private final AtomicLong accepted = new AtomicLong();
        private final AtomicLong rejectedOoo = new AtomicLong();
        private final AtomicLong writeMs = new AtomicLong();
    }
}