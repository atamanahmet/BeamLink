package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.peer.exception.ChunkHttpException;
import com.atamanahmet.beamlink.peer.exception.ChunkRetryExhaustedException;
import com.atamanahmet.beamlink.peer.exception.FileTransferException;
import com.atamanahmet.beamlink.peer.exception.OffsetMismatchException;
import com.atamanahmet.beamlink.peer.exception.TransferProtocolException;
import com.atamanahmet.beamlink.peer.config.TransferProperties;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.constants.TransferConstants;
import com.atamanahmet.beamlink.peer.util.FileChecksum;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSender {

    private final FileTransferService fileTransferService;
    private final ObjectMapper objectMapper;
    private final TransferHttpClient transferHttpClient;
    private final TransferSideResolver sideResolver;
    private final TransferSpeedLimiter transferSpeedLimiter;
    private final TransferProperties transferProperties;

    private final ConcurrentHashMap<UUID, Object> transferLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> activeSends = new ConcurrentHashMap<>();
    private static final long PROGRESS_SAVE_INTERVAL_MS = 1000L;

    @Async
    public void sendAsync(UUID transferId, String targetIp, int targetPort) {
        doSend(transferId, targetIp, targetPort);
    }

    public CompletableFuture<Void> sendBlocking(UUID transferId, String targetIp, int targetPort) {
        doSend(transferId, targetIp, targetPort);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Entry point. Opens file, sends chunks in order, cleans up.
     * Tracks highest confirmed offset seen across all chunks.
     */
    private void doSend(UUID transferId, String targetIp, int targetPort) {
        if (activeSends.putIfAbsent(transferId, Boolean.TRUE) != null) {
            log.debug("Send already running for {}, skipping second start", transferId);
            return;
        }

        log.info("doSend started transferId: {}", transferId);

        FileTransfer transfer = fileTransferService.findByTransferId(transferId).orElse(null);
        if (transfer == null) {
            log.warn("Transfer not found, aborting: {}", transferId);
            activeSends.remove(transferId);
            return;
        }

        ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();

        AtomicLong maxConfirmed = new AtomicLong(0L);
        AtomicLong lastProgressSaveAt = new AtomicLong(0L);

        String baseUrl = transferHttpClient.baseUrl(targetIp, targetPort);

        try (RandomAccessFile raf = new RandomAccessFile(
                Paths.get(transfer.getFilePath()).toFile(), "r")) {

            long startOffset = transferHttpClient.resolveStartOffset(
                    targetIp, targetPort, transferId, transfer.getConfirmedOffset());
            if (startOffset != transfer.getConfirmedOffset()) {
                log.info("Correcting start offset for {} from {} to {}",
                        transferId, transfer.getConfirmedOffset(), startOffset);
                transfer.setConfirmedOffset(startOffset);
                fileTransferService.save(transfer);
            }
            maxConfirmed.set(startOffset);

            readAndDispatchChunks(
                    raf, transfer, targetIp, targetPort, baseUrl, errors,
                    maxConfirmed, lastProgressSaveAt);
            drainErrors(errors);
            flushProgress(transferId, maxConfirmed, lastProgressSaveAt);
            markCompleteIfFullySent(transferId);

        } catch (Exception e) {
            FileTransfer current = fileTransferService.findByTransferId(transferId).orElse(null);
            if (current == null) {
                return;
            }
            flushProgress(transferId, maxConfirmed, lastProgressSaveAt);
            SendFailureKind kind = SendFailureRule.classify(e);
            if (kind == SendFailureKind.OFFLINE) {
                log.warn("Peer unreachable after retries, pausing: {}", transferId, e);
                markPausedOfflinePeer(current);
            } else if (kind == SendFailureKind.RETRY
                    || kind == SendFailureKind.RESYNC
                    || kind == SendFailureKind.CHUNK_CORRUPT) {
                log.warn("Send stopped, pausing source: {}", transferId, e);
                markPausedSource(current, e.getMessage());
            } else {
                log.error("Transfer failed: {}", transferId, e);
                markFailed(current, e.getMessage());
            }
        } finally {
            activeSends.remove(transferId);
            transferSpeedLimiter.clearTransfer(transferId);
        }
    }

    /**
     * Reads file chunk by chunk, checks pause/cancel each iteration,
     * sends each chunk in order.
     */
    private void readAndDispatchChunks(
            RandomAccessFile raf,
            FileTransfer transfer,
            String targetIp,
            int targetPort,
            String baseUrl,
            ConcurrentLinkedQueue<Exception> errors,
            AtomicLong maxConfirmed,
            AtomicLong lastProgressSaveAt) throws Exception {

        UUID transferId = transfer.getTransferId();
        long offset = transfer.getConfirmedOffset();
        raf.seek(offset);
        byte[] buffer = new byte[transferProperties.getChunkSizeBytes()];
        int bytesRead;
        Long lastResyncExpected = null;
        int resyncNoProgressCount = 0;

        while (true) {
            bytesRead = raf.read(buffer);
            if (bytesRead == -1) {
                break;
            }

            FileTransfer current = fileTransferService.findByTransferId(transferId).orElse(null);
            if (current == null) {
                log.warn("Transfer disappeared during send: {}", transferId);
                break;
            }
            if (current.getStatus() == TransferStatus.CANCELLED
                    || current.getStatus() == TransferStatus.FAILED) {
                log.info("Transfer stopped ({}): {}", current.getStatus(), transferId);
                flushProgress(transferId, maxConfirmed, lastProgressSaveAt);
                break;
            }
            if (current.getStatus() == TransferStatus.PAUSED) {
                flushProgress(transferId, maxConfirmed, lastProgressSaveAt);
                boolean leave = holdWhilePaused(transferId);
                if (leave) {
                    break;
                }
                FileTransfer resumed = fileTransferService.findByTransferId(transferId).orElse(null);
                if (resumed == null) {
                    break;
                }
                offset = transferHttpClient.resolveStartOffset(
                        targetIp, targetPort, transferId, resumed.getConfirmedOffset());
                if (offset != resumed.getConfirmedOffset()) {
                    log.info("Correcting resume offset for {} from {} to {}",
                            transferId, resumed.getConfirmedOffset(), offset);
                    resumed.setConfirmedOffset(offset);
                    fileTransferService.save(resumed);
                }
                maxConfirmed.set(offset);
                raf.seek(offset);
                lastResyncExpected = null;
                resyncNoProgressCount = 0;
                continue;
            }
            if (!errors.isEmpty()) {
                Exception error = errors.poll();
                if (error instanceof OffsetMismatchException mismatch) {
                    drainErrors(errors);
                    while (!errors.isEmpty() && errors.peek() instanceof OffsetMismatchException) {
                        errors.poll();
                    }
                    if (!errors.isEmpty()) {
                        throw errors.poll();
                    }
                    long expectedOffset = mismatch.getExpectedOffset();
                    resyncNoProgressCount = OffsetResyncRule.nextNoProgressCount(
                            expectedOffset, lastResyncExpected, resyncNoProgressCount);
                    lastResyncExpected = expectedOffset;
                    if (OffsetResyncRule.decide(
                            expectedOffset, current.getFileSize(), resyncNoProgressCount)
                            == OffsetResyncRule.Decision.PAUSE) {
                        flushProgress(transferId, maxConfirmed, lastProgressSaveAt);
                        markPausedSource(current, mismatch.getMessage());
                        break;
                    }
                    log.warn(
                            "Resync send offset transferId={} expectedOffset={}",
                            transferId, expectedOffset);
                    offset = expectedOffset;
                    maxConfirmed.set(expectedOffset);
                    current.setConfirmedOffset(expectedOffset);
                    fileTransferService.save(current);
                    raf.seek(expectedOffset);
                    continue;
                }
                throw error;
            }

            byte[] chunk = Arrays.copyOf(buffer, bytesRead);
            long chunkOffset = offset;
            long chunkEnd = offset + bytesRead - 1L;
            FileTransfer dispatchTransfer = current;

            dispatchChunk(
                    dispatchTransfer, targetIp, targetPort, baseUrl, chunkOffset, chunkEnd, chunk, errors,
                    maxConfirmed, lastProgressSaveAt
            );

            offset += bytesRead;
        }
    }

    /**
     * Sends one chunk with retry, updates progress on success, marks complete if last chunk.
     */
    private void dispatchChunk(
            FileTransfer transfer,
            String targetIp,
            int targetPort,
            String baseUrl,
            long chunkOffset,
            long chunkEnd,
            byte[] chunk,
            ConcurrentLinkedQueue<Exception> errors,
            AtomicLong maxConfirmed,
            AtomicLong lastProgressSaveAt) {
        try {
            assertSendStillRunnable(transfer.getTransferId());

            transferSpeedLimiter.acquire(transfer.getTransferId(), transfer.getSpeedLimitMbps(), chunk.length);

            ChunkAckResponse ack = sendChunkWithRetry(
                    targetIp, targetPort, baseUrl, transfer.getTransferId(),
                    chunkOffset, chunkEnd,
                    transfer.getFileSize(), chunk,
                    transfer.getMaxRetries()
            );

            updateTransferProgress(
                    transfer, ack.getConfirmedOffset(), maxConfirmed, lastProgressSaveAt,
                    ack.isComplete());

            if (ack.isComplete()) {
                transfer.setStatus(TransferStatus.COMPLETED);
                fileTransferService.save(transfer);
                transferLocks.remove(transfer.getTransferId());
                log.info("Transfer completed: {}", transfer.getTransferId());
            }
        } catch (TransferProtocolException e) {
            if ("TRANSFER_PAUSED".equals(e.getCode())
                    || DiskReserveRule.CODE.equals(e.getCode())) {
                flushProgress(
                        transfer.getTransferId(), maxConfirmed, lastProgressSaveAt);
                PauseOwner pausedBy = e.getPausedBy() != null
                        ? e.getPausedBy()
                        : PauseOwner.TARGET;
                holdForPeerPause(transfer, pausedBy);
                if (DiskReserveRule.CODE.equals(e.getCode())) {
                    transfer.setFailureReason(DiskReserveRule.CODE);
                    fileTransferService.save(transfer);
                }
            } else if ("TRANSFER_ABORTED".equals(e.getCode())) {
                flushProgress(
                        transfer.getTransferId(), maxConfirmed, lastProgressSaveAt);
                log.info("Chunk task aborted transferId={}: {}",
                        transfer.getTransferId(), e.getMessage());
            } else {
                errors.add(e);
            }
        } catch (Exception e) {
            errors.add(e);
        }
    }

    /**
     * Throws the first queued chunk error if any.
     */
    private void drainErrors(
            ConcurrentLinkedQueue<Exception> errors
    ) throws Exception {
        if (!errors.isEmpty()) throw errors.poll();
    }

    /**
     * Only advances if newOffset beats current max. DB save at most once per second.
     */
    private void updateTransferProgress(
            FileTransfer transfer,
            long newOffset,
            AtomicLong maxConfirmed,
            AtomicLong lastProgressSaveAt,
            boolean forceSave) {

        long prev;
        do {
            prev = maxConfirmed.get();
            if (newOffset <= prev) return;
        } while (!maxConfirmed.compareAndSet(prev, newOffset));

        Object lock = transferLocks.computeIfAbsent(transfer.getTransferId(), id -> new Object());
        synchronized (lock) {
            Instant now = Instant.now();
            if (transfer.getLastChunkAt() != null) {
                long delta = now.toEpochMilli() - transfer.getLastChunkAt().toEpochMilli();
                transfer.setActiveTransferMs(transfer.getActiveTransferMs() + delta);
            }
            transfer.setConfirmedOffset(newOffset);
            transfer.setLastChunkAt(now);
            long nowMs = System.currentTimeMillis();
            if (!forceSave && nowMs - lastProgressSaveAt.get() < PROGRESS_SAVE_INTERVAL_MS) {
                return;
            }
            fileTransferService.save(transfer);
            lastProgressSaveAt.set(nowMs);
        }
    }

    /**
     * Writes the highest confirmed offset to the DB now.
     */
    private void flushProgress(
            UUID transferId,
            AtomicLong maxConfirmed,
            AtomicLong lastProgressSaveAt) {
        FileTransfer transfer = fileTransferService.findByTransferId(transferId).orElse(null);
        if (transfer == null) {
            return;
        }
        Object lock = transferLocks.computeIfAbsent(transferId, id -> new Object());
        synchronized (lock) {
            long offset = maxConfirmed.get();
            Instant now = Instant.now();
            if (transfer.getLastChunkAt() != null) {
                long delta = now.toEpochMilli() - transfer.getLastChunkAt().toEpochMilli();
                transfer.setActiveTransferMs(transfer.getActiveTransferMs() + delta);
            }
            transfer.setConfirmedOffset(offset);
            transfer.setLastChunkAt(now);
            fileTransferService.save(transfer);
            lastProgressSaveAt.set(System.currentTimeMillis());
        }
    }

    /**
     * Marks COMPLETED when every byte is confirmed, including a 0 byte file.
     */
    private void markCompleteIfFullySent(UUID transferId) {
        FileTransfer sent = fileTransferService.findByTransferId(transferId).orElse(null);
        if (sent == null) {
            return;
        }
        if (!SendCompleteRule.shouldMarkComplete(
                sent.getStatus(), sent.getConfirmedOffset(), sent.getFileSize())) {
            return;
        }
        sent.setStatus(TransferStatus.COMPLETED);
        fileTransferService.save(sent);
        transferLocks.remove(transferId);
        log.info("Transfer completed: {}", transferId);
    }

    private ChunkAckResponse sendChunkWithRetry(
            String targetIp, int targetPort,
            String baseUrl, UUID transferId, long offset, long chunkEnd,
            long fileSize, byte[] chunk, int maxRetries
    ) throws IOException, InterruptedException {

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            assertSendStillRunnable(transferId);
            try {
                return sendChunk(targetIp, targetPort, baseUrl, transferId, offset, chunkEnd, fileSize, chunk);
            } catch (Exception e) {
                SendFailureKind kind = SendFailureRule.classify(e);
                if (!SendFailureRule.allowsChunkRetry(kind)) {
                    if (e instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new FileTransferException(e.getMessage(), e);
                }
                lastException = e;
                log.trace("Chunk send failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    assertSendStillRunnable(transferId);
                    Thread.sleep(TransferConstants.RETRY_DELAY_MS * attempt);
                }
            }
        }

        throw new ChunkRetryExhaustedException(
                "Chunk failed after " + maxRetries + " attempts at offset " + offset, lastException);
    }

    /**
     * Stops chunk work when transfer is FAILED, PAUSED, or CANCELLED.
     */
    private void assertSendStillRunnable(UUID transferId) {
        FileTransfer current = fileTransferService.findByTransferId(transferId).orElse(null);
        if (current == null) {
            throw new TransferProtocolException(
                    "TRANSFER_ABORTED",
                    "Transfer missing",
                    HttpStatus.CONFLICT);
        }
        TransferStatus status = current.getStatus();
        if (status == TransferStatus.PAUSED) {
            throw new TransferProtocolException(
                    "TRANSFER_PAUSED",
                    "Transfer is paused",
                    HttpStatus.CONFLICT,
                    current.getPausedBy());
        }
        if (status == TransferStatus.FAILED || status == TransferStatus.CANCELLED) {
            throw new TransferProtocolException(
                    "TRANSFER_ABORTED",
                    "Transfer " + status,
                    HttpStatus.CONFLICT);
        }
    }

    private ChunkAckResponse sendChunk(
            String targetIp, int targetPort,
            String baseUrl, UUID transferId, long offset, long chunkEnd,
            long fileSize, byte[] chunk
    ) throws IOException, InterruptedException {

        String contentRange = "bytes " + offset + "-" + chunkEnd + "/" + fileSize;
        String chunkSha256 = FileChecksum.sha256Hex(chunk);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/transfers/" + transferId + "/chunk"))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Range", contentRange)
                .header(TransferConstants.CHUNK_SHA256_HEADER, chunkSha256)
                .timeout(Duration.ofSeconds(TransferConstants.CHUNK_TIMEOUT_SECONDS))
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build();

        HttpResponse<String> response = transferHttpClient.exchange(request, targetIp, targetPort);
        if (response.statusCode() == 422) {
            throw new TransferProtocolException(
                    "CHUNK_CHECKSUM_MISMATCH",
                    "CHUNK_CHECKSUM_MISMATCH",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (response.statusCode() == 404) {
            JsonNode lostBody = jacksonOrIo(() -> objectMapper.readTree(response.body()));
            if (SessionLostRule.isLost(lostBody.path("code").asText())) {
                throw new TransferProtocolException(
                        SessionLostRule.CODE,
                        SessionLostRule.MESSAGE,
                        HttpStatus.NOT_FOUND);
            }
        }
        if (response.statusCode() == 507) {
            throw new TransferProtocolException(
                    DiskReserveRule.CODE,
                    DiskReserveRule.DETAIL,
                    HttpStatus.INSUFFICIENT_STORAGE,
                    PauseOwner.TARGET);
        }
        if (response.statusCode() == 409) {
            JsonNode body = jacksonOrIo(() -> objectMapper.readTree(response.body()));
            String code = body.path("code").asText();
            if ("TRANSFER_PAUSED".equals(code)) {
                PauseOwner pausedBy = null;
                String pausedByText = body.path("pausedBy").asText(null);
                if (pausedByText != null && !pausedByText.isBlank()) {
                    pausedBy = PauseOwner.valueOf(pausedByText);
                }
                throw new TransferProtocolException(
                        "TRANSFER_PAUSED",
                        "Peer transfer is paused",
                        HttpStatus.CONFLICT,
                        pausedBy);
            }
            if ("OFFSET_MISMATCH".equals(code)) {
                throw new OffsetMismatchException(body.path("expectedOffset").asLong(-1L));
            }
        }
        if (response.statusCode() != 200) {
            throw new ChunkHttpException(
                    response.statusCode(),
                    "Chunk rejected. Status: " + response.statusCode()
                            + " Body: " + response.body());
        }

        return jacksonOrIo(() -> objectMapper.readValue(response.body(), ChunkAckResponse.class));
    }

    /**
     * Runs a Jackson read. Unchecked Jackson errors become IOException.
     */
    private <T> T jacksonOrIo(Supplier<T> read) throws IOException {
        try {
            return read.get();
        } catch (JacksonException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Holds local send when the peer reports TRANSFER_PAUSED.
     */
    private void holdForPeerPause(FileTransfer transfer, PauseOwner pausedBy) {
        if (pausedBy == null) {
            throw new IllegalArgumentException("pausedBy required");
        }
        PauseOwner resolved = PausedByResolver.resolvePausedBy(transfer.getPausedBy(), pausedBy);
        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setPausedBy(resolved);
        fileTransferService.save(transfer);
        log.info("Holding send for peer pause transferId={} pausedBy={}",
                transfer.getTransferId(), resolved);
    }

    /**
     * Polls the row every PAUSE_HOLD_POLL_MS until ACTIVE or CANCELLED.
     * Returns true when the send loop should exit.
     */
    private boolean holdWhilePaused(UUID transferId) throws InterruptedException {
        log.info("Transfer paused, holding at current offset: {}", transferId);
        while (true) {
            Thread.sleep(TransferConstants.PAUSE_HOLD_POLL_MS);
            FileTransfer current = fileTransferService.findByTransferId(transferId).orElse(null);
            if (current == null) {
                return true;
            }
            if (current.getStatus() == TransferStatus.CANCELLED) {
                log.info("Transfer cancelled while held: {}", transferId);
                return true;
            }
            if (current.getStatus() == TransferStatus.ACTIVE) {
                log.info("Transfer resumed, will sync offset from receiver: {}", transferId);
                return false;
            }
            if (current.getStatus() == TransferStatus.FAILED) {
                log.info("Transfer failed while held: {}", transferId);
                return true;
            }
        }
    }

    private void markFailed(FileTransfer transfer, String reason) {
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason(reason);
        fileTransferService.save(transfer);
        transferLocks.remove(transfer.getTransferId());
        if (transfer.getTargetIp() != null && transfer.getTargetPort() != null) {
            transferHttpClient.notifyFailToPeer(
                    transfer.getTargetIp(),
                    transfer.getTargetPort(),
                    transfer.getTransferId(),
                    reason);
        }
    }

    /**
     * Pauses sender after retries with pausedBy SOURCE and notifies peer.
     */
    private void markPausedSource(FileTransfer transfer, String reason) {
        PauseOwner resolved = PausedByResolver.resolvePausedBy(
                transfer.getPausedBy(), PauseOwner.SOURCE);
        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setPausedBy(resolved);
        fileTransferService.save(transfer);
        transferLocks.remove(transfer.getTransferId());
        if (transfer.getTargetIp() != null && transfer.getTargetPort() != null) {
            transferHttpClient.notifyPauseToPeer(
                    transfer.getTargetIp(),
                    transfer.getTargetPort(),
                    transfer.getTransferId(),
                    resolved);
        }
        log.warn("Paused after retries transferId={} reason={}", transfer.getTransferId(), reason);
    }

    /**
     * Pauses with pausedBy set to the unreachable peer side. No notify.
     */
    private void markPausedOfflinePeer(FileTransfer transfer) {
        PauseOwner local = sideResolver.resolve(
                transfer.getSourceDeviceId(), transfer.getTargetDeviceId(), transfer.getTargetIp());
        PauseOwner offline = TransferSideResolver.opposite(local);
        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setPausedBy(offline);
        fileTransferService.save(transfer);
        transferLocks.remove(transfer.getTransferId());
        log.info("Paused unreachable peer transferId={} pausedBy={}",
                transfer.getTransferId(), offline);
    }
}
