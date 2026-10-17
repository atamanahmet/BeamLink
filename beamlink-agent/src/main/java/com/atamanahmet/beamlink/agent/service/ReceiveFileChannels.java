package com.atamanahmet.beamlink.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps one FileChannel per active receive transfer for positional writes.
 */
@Slf4j
@Service
public class ReceiveFileChannels implements DisposableBean {

    private static final int BUFFER_SIZE = 8192;

    private final ConcurrentHashMap<UUID, FileChannel> channels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicLong> openCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicLong> closeCounts = new ConcurrentHashMap<>();

    /**
     * Writes a chunk at offset. Opens the channel on first use for this transfer.
     */
    public long write(UUID transferId, Path path, long offset, InputStream stream) {
        FileChannel channel = channels.computeIfAbsent(transferId, id -> open(id, path));
        long bytesWritten = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            long position = offset;
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                ByteBuffer buf = ByteBuffer.wrap(buffer, 0, bytesRead);
                while (buf.hasRemaining()) {
                    int n = channel.write(buf, position);
                    if (n < 0) {
                        throw new IllegalStateException(
                                "FileChannel write returned " + n + " at " + position);
                    }
                    position += n;
                    bytesWritten += n;
                }
            }
            return bytesWritten;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to write chunk at offset " + offset, e);
        }
    }

    /**
     * Closes the channel for one transfer. Safe if already closed.
     */
    public void close(UUID transferId) {
        FileChannel channel = channels.remove(transferId);
        if (channel == null) {
            return;
        }
        closeCounts.computeIfAbsent(transferId, id -> new AtomicLong()).incrementAndGet();
        try {
            channel.close();
        } catch (IOException e) {
            log.warn("Could not close receive channel for {}", transferId, e);
        }
    }

    /**
     * TEMP-P0 open count for one transfer.
     */
    public long tempP0OpenCount(UUID transferId) {
        AtomicLong n = openCounts.get(transferId);
        return n == null ? 0L : n.get();
    }

    /**
     * TEMP-P0 close count for one transfer.
     */
    public long tempP0CloseCount(UUID transferId) {
        AtomicLong n = closeCounts.get(transferId);
        return n == null ? 0L : n.get();
    }

    /**
     * Clears TEMP-P0 open and close counts for one transfer.
     */
    public void clearTempP0Counts(UUID transferId) {
        openCounts.remove(transferId);
        closeCounts.remove(transferId);
    }

    /**
     * Closes every open receive channel on shutdown.
     */
    @Override
    public void destroy() {
        for (UUID id : new ArrayList<>(channels.keySet())) {
            close(id);
        }
    }

    private FileChannel open(UUID transferId, Path path) {
        openCounts.computeIfAbsent(transferId, id -> new AtomicLong()).incrementAndGet();
        try {
            return FileChannel.open(path, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to open partial file: " + path, e);
        }
    }
}
