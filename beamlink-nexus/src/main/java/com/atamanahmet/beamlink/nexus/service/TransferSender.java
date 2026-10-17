package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.nexus.exception.ChunkHttpException;
import com.atamanahmet.beamlink.nexus.exception.ChunkRetryExhaustedException;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import com.atamanahmet.beamlink.nexus.config.TransferProperties;
import com.atamanahmet.beamlink.nexus.http.HttpSender;
import com.atamanahmet.beamlink.nexus.http.TransferHttpClient;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.constants.TransferConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSender {

    private final FileTransferRepository transferRepository;
    private final ObjectMapper objectMapper;
    private final HttpSender httpSender;
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
     * Entry point. Opens file, runs sliding window dispatch, cleans up.
     * Tracks highest confirmed offset seen across all chunks.
     * Prevents regression on out-of-order completions.
     */
    private void doSend(UUID transferId, String targetIp, int targetPort) {
        if (activeSends.putIfAbsent(transferId, Boolean.TRUE) != null) {
            log.debug("Send already running for {}, skipping second start", transferId);
            return;
        }

        log.info("doSend started transferId: {}", transferId);

        FileTransfer transfer = transferRepository.findByTransferId(transferId).orElse(null);
        if (transfer == null) {
            log.warn("Transfer not found, aborting: {}", transferId);
            activeSends.remove(transferId);
            return;
        }

        int windowSize = transferProperties.getWindowSize();
        ExecutorService windowExecutor = Executors.newFixedThreadPool(windowSize);
        Semaphore window = new Semaphore(windowSize);
        ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();

        AtomicLong maxConfirmed = new AtomicLong(0L);
        AtomicLong lastProgressSaveAt = new AtomicLong(0L);
        TempP0Timing tempP0 = new TempP0Timing();

        String baseUrl = "http://" + targetIp + ":" + targetPort;

        try (RandomAccessFile raf = new RandomAccessFile(
                Paths.get(transfer.getFilePath()).toFile(), "r")) {

            long startOffset = transferHttpClient.resolveStartOffset(
                    targetIp, targetPort, transferId, transfer.getConfirmedOffset());
            if (startOffset != transfer.getConfirmedOffset()) {
                log.info("Correcting start offset for {} from {} to {}",
                        transferId, transfer.getConfirmedOffset(), startOffset);
                transfer.setConfirmedOffset(startOffset);
                transferRepository.save(transfer);
            }
            maxConfirmed.set(startOffset);

            readAndDispatchChunks(
                    raf, transfer, targetIp, targetPort, baseUrl, window, windowExecutor, errors,
                    maxConfirmed, lastProgressSaveAt, tempP0);
            drainWindow(window, errors);
            flushProgress(transferId, maxConfirmed, lastProgressSaveAt, tempP0);

        } catch (Exception e) {
            FileTransfer current = transferRepository.findByTransferId(transferId).orElse(null);
            if (current == null) {
                return;
            }
            flushProgress(transferId, maxConfirmed, lastProgressSaveAt, tempP0);
            SendFailureKind kind = SendFailureRule.classify(e);
            if (kind == SendFailureKind.OFFLINE) {
                log.warn("Peer unreachable after retries, pausing: {}", transferId, e);
                markPausedOfflinePeer(current);
                tempP0.logSummary(transferId, "pause");
            } else if (kind == SendFailureKind.RETRY) {
                log.warn("Retries exhausted, pausing source: {}", transferId, e);
                markPausedSource(current, e.getMessage());
                tempP0.logSummary(transferId, "pause");
            } else {
                log.error("Transfer failed: {}", transferId, e);
                markFailed(current, e.getMessage());
                tempP0.logSummary(transferId, "failure");
            }
        } finally {
            windowExecutor.shutdown();
            activeSends.remove(transferId);
            transferSpeedLimiter.clearTransfer(transferId);
        }
    }

    /**
     * Reads file chunk by chunk, checks pause/cancel each iteration,
     * submits each chunk to window executor.
     */
    private void readAndDispatchChunks(
            RandomAccessFile raf,
            FileTransfer transfer,
            String targetIp,
            int targetPort,
            String baseUrl,
            Semaphore window,
            ExecutorService windowExecutor,
            ConcurrentLinkedQueue<Exception> errors,
            AtomicLong maxConfirmed,
            AtomicLong lastProgressSaveAt,
            TempP0Timing tempP0
    ) throws Exception {

        UUID transferId = transfer.getTransferId();
        long offset = transfer.getConfirmedOffset();
        raf.seek(offset);
        byte[] buffer = new byte[transferProperties.getChunkSizeBytes()];
        int bytesRead;

        while (true) {
            long readStart = System.nanoTime();
            bytesRead = raf.read(buffer);
            tempP0.readMs.addAndGet((System.nanoTime() - readStart) / 1_000_000L);
            if (bytesRead == -1) {
                break;
            }

            FileTransfer current = transferRepository.findByTransferId(transferId).orElse(null);
            if (current == null) {
                log.warn("Transfer disappeared during send: {}", transferId);
                break;
            }
            if (current.getStatus() == TransferStatus.CANCELLED
                    || current.getStatus() == TransferStatus.FAILED) {
                log.info("Transfer stopped ({}): {}", current.getStatus(), transferId);
                flushProgress(transferId, maxConfirmed, lastProgressSaveAt, tempP0);
                tempP0.logSummary(transferId, "cancel");
                break;
            }
            if (current.getStatus() == TransferStatus.PAUSED) {
                flushProgress(transferId, maxConfirmed, lastProgressSaveAt, tempP0);
                tempP0.logSummary(transferId, "pause");
                boolean leave = holdWhilePaused(transferId);
                if (leave) {
                    break;
                }
                tempP0.summaryLogged.set(false);
                FileTransfer resumed = transferRepository.findByTransferId(transferId).orElse(null);
                if (resumed == null) {
                    break;
                }
                offset = transferHttpClient.resolveStartOffset(
                        targetIp, targetPort, transferId, resumed.getConfirmedOffset());
                if (offset != resumed.getConfirmedOffset()) {
                    log.info("Correcting resume offset for {} from {} to {}",
                            transferId, resumed.getConfirmedOffset(), offset);
                    resumed.setConfirmedOffset(offset);
                    transferRepository.save(resumed);
                }
                maxConfirmed.set(offset);
                raf.seek(offset);
                continue;
            }
            if (!errors.isEmpty()) throw errors.poll();

            byte[] chunk = Arrays.copyOf(buffer, bytesRead);
            long chunkOffset = offset;
            long chunkEnd = offset + bytesRead - 1L;
            FileTransfer dispatchTransfer = current;

            window.acquire();
            windowExecutor.submit(() -> dispatchChunk(
                    dispatchTransfer, baseUrl, chunkOffset, chunkEnd, chunk, window, errors,
                    maxConfirmed, lastProgressSaveAt, tempP0
            ));

            tempP0.maybeLog();
            offset += bytesRead;
        }
    }

    /**
     * Sends one chunk with retry, updates progress on success, marks complete if last chunk.
     */
    private void dispatchChunk(
            FileTransfer transfer,
            String baseUrl,
            long chunkOffset,
            long chunkEnd,
            byte[] chunk,
            Semaphore window,
            ConcurrentLinkedQueue<Exception> errors,
            AtomicLong maxConfirmed,
            AtomicLong lastProgressSaveAt,
            TempP0Timing tempP0
    ) {
        try {
            assertSendStillRunnable(transfer.getTransferId());

            long waitMs = transferSpeedLimiter.acquire(
                    transfer.getTransferId(), transfer.getSpeedLimitMbps(), chunk.length);
            tempP0.sleepMs.addAndGet(waitMs);

            long httpStart = System.nanoTime();
            ChunkAckResponse ack = sendChunkWithRetry(
                    baseUrl, transfer.getTransferId(),
                    chunkOffset, chunkEnd,
                    transfer.getFileSize(), chunk,
                    transfer.getMaxRetries(),
                    tempP0
            );
            tempP0.httpMs.addAndGet((System.nanoTime() - httpStart) / 1_000_000L);
            tempP0.windowBytes.addAndGet(chunk.length);

            updateTransferProgress(
                    transfer, ack.getConfirmedOffset(), maxConfirmed, lastProgressSaveAt,
                    ack.isComplete(), tempP0);
            tempP0.maybeLog();

            if (ack.isComplete()) {
                transfer.setStatus(TransferStatus.COMPLETED);
                transferRepository.save(transfer);
                transferLocks.remove(transfer.getTransferId());
                log.info("Transfer completed: {}", transfer.getTransferId());
                tempP0.logSummary(transfer.getTransferId(), "complete");
            }
        } catch (TransferProtocolException e) {
            if ("TRANSFER_PAUSED".equals(e.getCode())) {
                flushProgress(
                        transfer.getTransferId(), maxConfirmed, lastProgressSaveAt, tempP0);
                holdForPeerPause(transfer, e.getPausedBy());
                tempP0.logSummary(transfer.getTransferId(), "pause");
            } else if ("TRANSFER_ABORTED".equals(e.getCode())) {
                flushProgress(
                        transfer.getTransferId(), maxConfirmed, lastProgressSaveAt, tempP0);
                log.info("Chunk task aborted transferId={}: {}",
                        transfer.getTransferId(), e.getMessage());
            } else {
                errors.add(e);
            }
        } catch (Exception e) {
            errors.add(e);
        } finally {
            window.release();
        }
    }

    /**
     * Blocks until all window slots free, means all in-flight chunks finished.
     * Then checks if any chunk failed.
     */
    private void drainWindow(
            Semaphore window,
            ConcurrentLinkedQueue<Exception> errors
    ) throws Exception {
        window.acquire(transferProperties.getWindowSize());
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
            boolean forceSave,
            TempP0Timing tempP0) {

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
            long dbStart = System.nanoTime();
            transferRepository.save(transfer);
            tempP0.dbMs.addAndGet((System.nanoTime() - dbStart) / 1_000_000L);
            lastProgressSaveAt.set(nowMs);
        }
    }

    /**
     * Writes the highest confirmed offset to the DB now.
     */
    private void flushProgress(
            UUID transferId,
            AtomicLong maxConfirmed,
            AtomicLong lastProgressSaveAt,
            TempP0Timing tempP0) {
        FileTransfer transfer = transferRepository.findByTransferId(transferId).orElse(null);
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
            long dbStart = System.nanoTime();
            transferRepository.save(transfer);
            tempP0.dbMs.addAndGet((System.nanoTime() - dbStart) / 1_000_000L);
            lastProgressSaveAt.set(System.currentTimeMillis());
        }
    }

    private ChunkAckResponse sendChunkWithRetry(
            String baseUrl, UUID transferId, long offset, long chunkEnd,
            long fileSize, byte[] chunk, int maxRetries, TempP0Timing tempP0
    ) throws IOException, InterruptedException {

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            assertSendStillRunnable(transferId);
            try {
                return sendChunk(baseUrl, transferId, offset, chunkEnd, fileSize, chunk);
            } catch (Exception e) {
                SendFailureKind kind = SendFailureRule.classify(e);
                if (kind == SendFailureKind.FATAL) {
                    if (e instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new FileTransferException(e.getMessage(), e);
                }
                lastException = e;
                log.trace("Chunk send failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    assertSendStillRunnable(transferId);
                    tempP0.retries.incrementAndGet();
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
        FileTransfer current = transferRepository.findByTransferId(transferId).orElse(null);
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
            String baseUrl, UUID transferId, long offset, long chunkEnd,
            long fileSize, byte[] chunk
    ) throws IOException, InterruptedException {

        String contentRange = "bytes " + offset + "-" + chunkEnd + "/" + fileSize;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/transfers/" + transferId + "/chunk"))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Range", contentRange)
                .timeout(Duration.ofSeconds(TransferConstants.CHUNK_TIMEOUT_SECONDS))
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build();

        HttpResponse<String> response = httpSender.send(request);
        if (response.statusCode() == 409) {
            JsonNode body = objectMapper.readTree(response.body());
            if ("TRANSFER_PAUSED".equals(body.path("code").asText())) {
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
        }
        if (response.statusCode() != 200) {
            throw new ChunkHttpException(
                    response.statusCode(),
                    "Chunk rejected. Status: " + response.statusCode()
                            + " Body: " + response.body());
        }

        return objectMapper.readValue(response.body(), ChunkAckResponse.class);
    }

    /**
     * Holds local send when the peer reports TRANSFER_PAUSED.
     */
    private void holdForPeerPause(FileTransfer transfer, PauseOwner pausedBy) {
        PauseOwner resolved = PausedByResolver.resolvePausedBy(transfer.getPausedBy(), pausedBy);
        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setPausedBy(resolved);
        transferRepository.save(transfer);
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
            FileTransfer current = transferRepository.findByTransferId(transferId).orElse(null);
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
        transferRepository.save(transfer);
        transferLocks.remove(transfer.getTransferId());
        if (transfer.getTargetIp() != null && transfer.getTargetPort() != null) {
            transferHttpClient.notifyCancelToPeer(
                    transfer.getTargetIp(), transfer.getTargetPort(), transfer.getTransferId());
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
        transferRepository.save(transfer);
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
                transfer.getSourceAgentId(), transfer.getTargetAgentId(), transfer.getTargetIp());
        PauseOwner offline = TransferSideResolver.opposite(local);
        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setPausedBy(offline);
        transferRepository.save(transfer);
        transferLocks.remove(transfer.getTransferId());
        log.info("Paused unreachable peer transferId={} pausedBy={}",
                transfer.getTransferId(), offline);
    }

    /**
     * TEMP-P0 per-transfer timing counters for temporary sender profiling.
     */
    private static final class TempP0Timing {
        private final AtomicLong readMs = new AtomicLong();
        private final AtomicLong httpMs = new AtomicLong();
        private final AtomicLong dbMs = new AtomicLong();
        private final AtomicLong sleepMs = new AtomicLong();
        private final AtomicLong windowBytes = new AtomicLong();
        private final AtomicLong retries = new AtomicLong();
        private final AtomicLong lastLogAt = new AtomicLong(System.currentTimeMillis());
        private final AtomicBoolean summaryLogged = new AtomicBoolean();

        private void maybeLog() {
            long now = System.currentTimeMillis();
            long prev = lastLogAt.get();
            if (now - prev < 2000L) {
                return;
            }
            if (!lastLogAt.compareAndSet(prev, now)) {
                return;
            }
            long elapsedMs = Math.max(1L, now - prev);
            long bytes = windowBytes.getAndSet(0L);
            double mbps = (bytes * 8.0) / elapsedMs / 1000.0;
            log.debug(
                    "TEMP-P0 achieved={} Mb/s payloadBytes={} windowMs={} readMs={} httpMs={} dbMs={} sleepMs={}",
                    String.format("%.1f", mbps),
                    bytes,
                    elapsedMs,
                    readMs.get(),
                    httpMs.get(),
                    dbMs.get(),
                    sleepMs.get());
        }

        /**
         * TEMP-P0 one summary line for sender counters.
         */
        private void logSummary(UUID transferId, String event) {
            if (!summaryLogged.compareAndSet(false, true)) {
                return;
            }
            log.info(
                    "TEMP-P0 sender event={} transferId={} retries={} waitMs={}",
                    event,
                    transferId,
                    retries.get(),
                    httpMs.get());
        }
    }
}
