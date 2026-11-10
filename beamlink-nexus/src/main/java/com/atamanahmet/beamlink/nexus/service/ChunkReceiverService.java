package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.BatchTransfer;
import com.atamanahmet.beamlink.nexus.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.nexus.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.nexus.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.InsufficientDiskException;
import com.atamanahmet.beamlink.nexus.exception.OffsetMismatchException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import com.atamanahmet.beamlink.nexus.config.TransferProperties;
import com.atamanahmet.beamlink.nexus.http.TransferHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private final FileTransferService fileTransferService;
    private final StorageService storageService;
    private final TransferLogService transferLogService;
    private final DirectoryTransferService directoryTransferService;
    private final BatchTransferService batchTransferService;
    private final ReceiveFileChannels receiveFileChannels;
    private final ReceiveStallTracker receiveStallTracker;
    private final TransferHttpClient transferHttpClient;
    private final TransferProperties transferProperties;
    private final DiskReserveProvider diskReserveProvider;

    private final ConcurrentHashMap<UUID, Long> liveConfirmedOffset = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastReceiverSaveAt = new ConcurrentHashMap<>();

    @Transactional
    public void prepareReceive(FileTransfer transfer) {
        FileSizeRule.requireValid(transfer.getFileSize());

        long required = transfer.getFileSize() - transfer.getConfirmedOffset();
        if (required < 0L) {
            required = 0L;
        }
        requireRoom(required);

        Path partialFile = storageService.resolvePartialPath(transfer.getFileName());
        storageService.allocatePartialFile(partialFile, transfer.getFileSize());
        transfer.setFilePath(partialFile.toString());
        fileTransferService.save(transfer);
        receiveStallTracker.touch(transfer.getTransferId());
        log.info("Prepared to receive: {} ({} bytes)", transfer.getFileName(), transfer.getFileSize());
        completeReceiveIfEmpty(transfer);
    }

    @Transactional
    public void prepareReceiveDirectory(ReceiveDirectoryRequest request) {
        DirectoryTransfer dt = DirectoryTransfer.initiate(
                request.getDirectoryTransferId(),
                request.getDispatchId(),
                request.getSourceDeviceId(),
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

        long required = 0L;
        for (ReceiveDirectoryRequest.FileEntry entry : request.getFiles()) {
            required += FileSizeRule.requireValid(entry.getFileSize());
        }
        requireRoom(required);

        directoryTransferService.save(dt);

        Path dirRoot = storageService.getUploadsDir().resolve(request.getDirectoryName());
        for (String emptyDir : dt.getEmptyDirectories()) {
            storageService.createDirectory(dirRoot.resolve(emptyDir));
        }

        List<FileTransfer> fileTransfers = new ArrayList<>();
        for (ReceiveDirectoryRequest.FileEntry entry : request.getFiles()) {
            long fileSize = FileSizeRule.requireValid(entry.getFileSize());
            FileTransfer ft = FileTransfer.initiate(
                    entry.getTransferId(),
                    request.getSourceDeviceId(),
                    null,
                    entry.getFileName(),
                    null,
                    fileSize
            );
            ft.setDirectoryTransferId(request.getDirectoryTransferId());
            ft.setRelativePath(entry.getRelativePath());
            ft.setDirectoryName(request.getDirectoryName());
            ft.setStatus(TransferStatus.ACTIVE);

            Path partialFile = storageService.resolvePartialPath(entry.getFileName());
            storageService.allocatePartialFile(partialFile, fileSize);
            ft.setFilePath(partialFile.toString());
            fileTransfers.add(ft);
        }

        fileTransferService.saveAll(fileTransfers);
        for (FileTransfer ft : fileTransfers) {
            receiveStallTracker.touch(ft.getTransferId());
            completeReceiveIfEmpty(ft);
        }
        log.info("Prepared to receive directory: {} ({} files)", request.getDirectoryName(), fileTransfers.size());
    }

    @Transactional
    public void prepareReceiveBatch(ReceiveBatchRequest request) {
        BatchTransfer bt = BatchTransfer.initiate(
                request.getBatchTransferId(),
                request.getDispatchId(),
                request.getSourceDeviceId(),
                null,
                null,
                0,
                request.getTotalFiles(),
                request.getTotalSize()
        );
        bt.setStatus(GroupTransferStatus.ACTIVE);

        long required = 0L;
        for (ReceiveBatchRequest.FileEntry entry : request.getFiles()) {
            required += FileSizeRule.requireValid(entry.getFileSize());
        }
        requireRoom(required);

        batchTransferService.save(bt);

        List<FileTransfer> fileTransfers = new ArrayList<>();
        for (ReceiveBatchRequest.FileEntry entry : request.getFiles()) {
            long fileSize = FileSizeRule.requireValid(entry.getFileSize());
            FileTransfer ft = FileTransfer.initiate(
                    entry.getTransferId(),
                    request.getSourceDeviceId(),
                    null,
                    entry.getFileName(),
                    null,
                    fileSize
            );
            ft.setBatchTransferId(request.getBatchTransferId());
            ft.setStatus(TransferStatus.ACTIVE);

            Path partialFile = storageService.resolvePartialPath(entry.getFileName());
            storageService.allocatePartialFile(partialFile, fileSize);
            ft.setFilePath(partialFile.toString());
            fileTransfers.add(ft);
        }

        fileTransferService.saveAll(fileTransfers);
        for (FileTransfer ft : fileTransfers) {
            receiveStallTracker.touch(ft.getTransferId());
            completeReceiveIfEmpty(ft);
        }
        log.info("Prepared to receive batch: {} ({} files)", request.getBatchTransferId(), fileTransfers.size());
    }

    /**
     * Hashes chunk bytes then writes. DB progress saved at most once per second, always on complete.
     */
    public ChunkAckResponse receiveChunk(
            UUID transferId,
            long offset,
            long length,
            InputStream chunkStream,
            String chunkSha256Header) {
        FileTransfer transfer = fileTransferService.findByTransferId(transferId)
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

        int maxChunk = transferProperties.getChunkSizeBytes();
        if (length < 0L || length > maxChunk) {
            throw new TransferProtocolException(
                    "INVALID_CHUNK_LENGTH",
                    "Chunk length out of bounds",
                    HttpStatus.BAD_REQUEST);
        }

        byte[] chunkBytes;
        try {
            chunkBytes = readExact(chunkStream, (int) length);
        } catch (IOException e) {
            throw new FileTransferException("Failed to read chunk body", e);
        }

        ChunkChecksumRule.Result hashResult = ChunkChecksumRule.check(chunkSha256Header, chunkBytes);
        if (hashResult == ChunkChecksumRule.Result.MISSING) {
            throw new TransferProtocolException(
                    "MISSING_CHUNK_SHA256",
                    "X-Chunk-SHA256 header required",
                    HttpStatus.BAD_REQUEST);
        }
        if (hashResult == ChunkChecksumRule.Result.MISMATCH) {
            throw new TransferProtocolException(
                    "CHUNK_CHECKSUM_MISMATCH",
                    "Chunk checksum mismatch",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        long expected = liveConfirmedOffset.getOrDefault(transferId, transfer.getConfirmedOffset());
        ReceiveOffsetRule.Decision decision = ReceiveOffsetRule.decide(offset, length, expected);
        if (decision == ReceiveOffsetRule.Decision.DUPLICATE) {
            return new ChunkAckResponse(expected, expected >= transfer.getFileSize());
        }
        if (decision != ReceiveOffsetRule.Decision.ACCEPT) {
            throw new OffsetMismatchException(expected);
        }

        if (!hasRoom(length)) {
            pauseForInsufficientDisk(transfer);
            throw new InsufficientDiskException();
        }

        Path partialFile = storageService.storedPartialPath(transfer);
        long bytesWritten;
        try {
            bytesWritten = receiveFileChannels.write(
                    transferId, partialFile, offset, new ByteArrayInputStream(chunkBytes));
        } catch (RuntimeException e) {
            if (causedByIo(e) && !hasRoom(length)) {
                pauseForInsufficientDisk(transfer);
                throw new InsufficientDiskException();
            }
            receiveFileChannels.close(transferId);
            receiveStallTracker.remove(transferId);
            throw e;
        }
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
            receiveFileChannels.close(transferId);
            if (!verifyFinalSizeOrFail(transfer, transferId)) {
                throw new TransferProtocolException(
                        "SIZE_MISMATCH",
                        "Confirmed size does not match expected size",
                        HttpStatus.CONFLICT);
            }
            transfer.setStatus(TransferStatus.COMPLETED);
            fileTransferService.save(transfer);
            storageService.moveToFinalLocation(transfer);
            logCompletedTransfer(transfer);
            log.info("Transfer completed: {}", transfer.getFileName());
            liveConfirmedOffset.remove(transferId);
            lastReceiverSaveAt.remove(transferId);
            receiveStallTracker.remove(transferId);
            checkAndCompleteParent(transfer);
        } else {
            long nowMs = System.currentTimeMillis();
            Long last = lastReceiverSaveAt.get(transferId);
            if (last == null || nowMs - last >= RECEIVER_SAVE_INTERVAL_MS) {
                fileTransferService.save(transfer);
                lastReceiverSaveAt.put(transferId, nowMs);
            }
        }

        return new ChunkAckResponse(newOffset, complete);
    }

    /**
     * Reads exactly length bytes from the stream into a new buffer.
     */
    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(buf, off, length - off);
            if (n < 0) {
                throw new IOException("Unexpected end of chunk body at " + off + " of " + length);
            }
            off += n;
        }
        return buf;
    }

    /**
     * Moves a 0 byte receive to COMPLETED. No chunks will arrive.
     */
    private void completeReceiveIfEmpty(FileTransfer transfer) {
        if (transfer.getFileSize() != 0L) {
            return;
        }
        transfer.setStatus(TransferStatus.COMPLETED);
        fileTransferService.save(transfer);
        storageService.moveToFinalLocation(transfer);
        logCompletedTransfer(transfer);
        log.info("Transfer completed: {}", transfer.getFileName());
        receiveStallTracker.remove(transfer.getTransferId());
        checkAndCompleteParent(transfer);
    }

    /**
     * Last chunk: confirmed bytes must equal file size. Marks FAILED on mismatch.
     */
    private boolean verifyFinalSizeOrFail(FileTransfer transfer, UUID transferId) {
        if (FinalSizeRule.matches(transfer.getConfirmedOffset(), transfer.getFileSize())) {
            return true;
        }
        log.warn("Size mismatch for transfer {}: confirmed={} expected={}",
                transferId, transfer.getConfirmedOffset(), transfer.getFileSize());
        markSizeMismatch(transfer, transferId);
        return false;
    }

    private void markSizeMismatch(FileTransfer transfer, UUID transferId) {
        storageService.deletePartialFile(transfer);
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason("SIZE_MISMATCH");
        fileTransferService.save(transfer);
        liveConfirmedOffset.remove(transferId);
        lastReceiverSaveAt.remove(transferId);
        receiveStallTracker.remove(transferId);
        if (transfer.getSourceDeviceId() != null && transfer.getSourceIp() != null) {
            transferHttpClient.notifyFailToPeer(
                    transfer.getSourceDeviceId(),
                    transfer.getSourceIp(),
                    transferId,
                    "SIZE_MISMATCH");
        }
        checkAndCompleteParent(transfer);
    }

    /**
     * Flushes live confirmedOffset to DB and closes the receive channel.
     */
    @Transactional
    public void onReceivePaused(UUID transferId) {
        fileTransferService.findByTransferId(transferId).ifPresent(ft -> {
            long live = liveConfirmedOffset.getOrDefault(transferId, ft.getConfirmedOffset());
            ft.setConfirmedOffset(live);
            fileTransferService.save(ft);
            liveConfirmedOffset.put(transferId, live);
            lastReceiverSaveAt.remove(transferId);
        });
        receiveFileChannels.close(transferId);
        receiveStallTracker.remove(transferId);
    }

    private void logCompletedTransfer(FileTransfer transfer) {
        try {
            double speedMbps = transfer.getActiveTransferMs() > 0
                    ? (double) transfer.getFileSize() * 8.0 / transfer.getActiveTransferMs() / 1000.0
                    : 0.0;
            transferLogService.logTransfer(
                    transfer.getSourceDeviceId(),
                    null,
                    transfer.getFileName(),
                    transfer.getFileSize(),
                    speedMbps
            );
        } catch (Exception e) {
            log.warn("Failed to log completed transfer, file was saved successfully", e);
        }
    }

    @Transactional
    protected void checkAndCompleteParent(FileTransfer transfer) {
        if (transfer.getBatchTransferId() != null) {
            UUID batchId = transfer.getBatchTransferId();
            List<FileTransfer> siblings = fileTransferService.findByBatchTransferId(batchId);
            if (hasInProgressChild(siblings)) {
                return;
            }

            BatchTransfer bt = batchTransferService.findById(batchId).orElse(null);
            if (bt == null) {
                return;
            }

            GroupTransferStatus status = GroupStatusRollup.fromChildren(
                    siblings.stream().map(FileTransfer::getStatus).toList());
            bt.setStatus(status);
            if (status == GroupTransferStatus.COMPLETED) {
                bt.setCompletedAt(Instant.now());
            }
            batchTransferService.save(bt);
            log.info("Batch rollup on receiver: {} -> {}", batchId, status);
        }

        if (transfer.getDirectoryTransferId() != null) {
            UUID dirId = transfer.getDirectoryTransferId();
            List<FileTransfer> siblings = fileTransferService.findByDirectoryTransferId(dirId);
            if (hasInProgressChild(siblings)) {
                return;
            }

            DirectoryTransfer dt = directoryTransferService.findById(dirId).orElse(null);
            if (dt == null) {
                return;
            }

            GroupTransferStatus status = GroupStatusRollup.fromChildren(
                    siblings.stream().map(FileTransfer::getStatus).toList());
            dt.setStatus(status);
            if (status == GroupTransferStatus.COMPLETED) {
                dt.setCompletedAt(Instant.now());
            }
            directoryTransferService.save(dt);
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
        return fileTransferService.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null))
                .getConfirmedOffset();
    }

    @Transactional
    public void cancelReceive(UUID transferId) {
        fileTransferService.findByTransferId(transferId).ifPresent(ft -> {
            if (ft.getStatus() == TransferStatus.ACTIVE
                    || ft.getStatus() == TransferStatus.PAUSED) {
                long live = liveConfirmedOffset.getOrDefault(transferId, ft.getConfirmedOffset());
                ft.setConfirmedOffset(live);
                ft.setStatus(TransferStatus.CANCELLED);
                fileTransferService.save(ft);
                receiveFileChannels.close(transferId);
                storageService.deletePartialFile(ft);
                liveConfirmedOffset.remove(transferId);
                lastReceiverSaveAt.remove(transferId);
                receiveStallTracker.remove(transferId);
            }
        });
    }

    /**
     * Peer fail-notify: mark receive side FAILED and delete partial.
     */
    @Transactional
    public void failReceive(UUID transferId, String reason) {
        fileTransferService.findByTransferId(transferId).ifPresent(ft -> {
            if (ft.getStatus() == TransferStatus.ACTIVE
                    || ft.getStatus() == TransferStatus.PAUSED
                    || ft.getStatus() == TransferStatus.PENDING) {
                long live = liveConfirmedOffset.getOrDefault(transferId, ft.getConfirmedOffset());
                ft.setConfirmedOffset(live);
                ft.setStatus(TransferStatus.FAILED);
                ft.setFailureReason(reason);
                fileTransferService.save(ft);
                receiveFileChannels.close(transferId);
                storageService.deletePartialFile(ft);
                liveConfirmedOffset.remove(transferId);
                lastReceiverSaveAt.remove(transferId);
                receiveStallTracker.remove(transferId);
                checkAndCompleteParent(ft);
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

    /**
     * True when usable space covers bytesNeeded plus the reserve.
     */
    private boolean hasRoom(long bytesNeeded) {
        return DiskReserveRule.allowed(
                storageService.usableBytes(),
                bytesNeeded,
                diskReserveProvider.getBytes());
    }

    /**
     * Prepare-time refusal. Does not pause a row.
     */
    private void requireRoom(long bytesNeeded) {
        if (!hasRoom(bytesNeeded)) {
            throw new InsufficientDiskException();
        }
    }

    /**
     * True when an IOException caused this runtime failure.
     */
    private static boolean causedByIo(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pauses the receive row, keeps the partial, tells the sender.
     */
    private void pauseForInsufficientDisk(FileTransfer transfer) {
        UUID transferId = transfer.getTransferId();
        long live = liveConfirmedOffset.getOrDefault(transferId, transfer.getConfirmedOffset());
        transfer.setConfirmedOffset(live);
        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setPausedBy(PauseOwner.TARGET);
        transfer.setFailureReason(DiskReserveRule.CODE);
        fileTransferService.save(transfer);
        liveConfirmedOffset.put(transferId, live);
        receiveFileChannels.close(transferId);
        receiveStallTracker.remove(transferId);
        lastReceiverSaveAt.remove(transferId);
        if (transfer.getSourceDeviceId() != null && transfer.getSourceIp() != null) {
            transferHttpClient.notifyPauseToPeer(
                    transfer.getSourceDeviceId(),
                    transfer.getSourceIp(),
                    transferId,
                    PauseOwner.TARGET,
                    DiskReserveRule.CODE);
        }
    }
}
