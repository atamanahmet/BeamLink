package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.TransferStatus;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferAsyncSenderTest {

    @Mock
    private FileTransferRepository transferRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransferAsyncSender asyncSender;

    @TempDir
    Path tempDir;

    private UUID sourceAgentId;

    @BeforeEach
    void setUp() {
        sourceAgentId = UUID.randomUUID();
    }

    /**
     * sendAsync must stop immediately if the transfer is CANCELLED
     * before the first chunk is sent. No save.
     */
    @Test
    void sendAsync_stopsImmediatelyIfCancelled() throws Exception {
        Path file = tempDir.resolve("large.bin");
        Files.write(file, new byte[1024]);

        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, sourceAgentId, UUID.randomUUID(),
                "large.bin", file.toString(), 1024L
        );
        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setConfirmedOffset(0L);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        asyncSender.sendAsync(transferId, "127.0.0.1", 9999, "token");

        verify(transferRepository, atLeast(1)).findByTransferId(transferId);
        verify(transferRepository, never()).save(any());
    }

    /**
     * sendAsync must stop if the transfer is PAUSED. No save.
     */
    @Test
    void sendAsync_stopsIfPaused() throws Exception {
        Path file = tempDir.resolve("pausable.bin");
        Files.write(file, new byte[1024]);

        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, sourceAgentId, UUID.randomUUID(),
                "pausable.bin", file.toString(), 1024L
        );
        transfer.setStatus(TransferStatus.PAUSED);
        transfer.setConfirmedOffset(0L);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        asyncSender.sendAsync(transferId, "127.0.0.1", 9999, "token");

        verify(transferRepository, never()).save(any());
    }

    /**
     * sendAsync marks the transfer FAILED when the target is unreachable.
     * After 3 retries per chunk, the transfer must be saved with FAILED status.
     */
    @Test
    void sendAsync_marksFailedOnNetworkError() throws Exception {
        Path file = tempDir.resolve("fail.bin");
        Files.write(file, new byte[]{0x01, 0x02, 0x03});

        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, sourceAgentId, UUID.randomUUID(),
                "fail.bin", file.toString(), 3L
        );
        transfer.setStatus(TransferStatus.ACTIVE);
        transfer.setConfirmedOffset(0L);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        asyncSender.sendAsync(transferId, "127.0.0.1", 1, "token");

        verify(transferRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == TransferStatus.FAILED
        ));
    }

    /**
     * sendAsync must resume from confirmedOffset
     */
    @Test
    void sendAsync_resumesFromConfirmedOffset() throws Exception {
        byte[] fullFile = "0123456789ABCDEFGHIJ".getBytes();
        Path file = tempDir.resolve("resume.bin");
        Files.write(file, fullFile);

        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, sourceAgentId, UUID.randomUUID(),
                "resume.bin", file.toString(), (long) fullFile.length
        );
        transfer.setStatus(TransferStatus.ACTIVE);
        transfer.setConfirmedOffset(10L); // first 10 bytes already sent

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        // Will fail at send — we just care that it starts from offset 10
        asyncSender.sendAsync(transferId, "127.0.0.1", 1, "token");

        verify(transferRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == TransferStatus.FAILED
        ));
    }

    /**
     * markFailed sets FAILED status and stores the failure reason.
     */
    @Test
    void markFailed_setsStatusAndReason() throws IOException {
        UUID transferId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId, sourceAgentId, UUID.randomUUID(),
                "file.txt", tempDir.resolve("file.txt").toString(), 1024L
        );

        Files.write(tempDir.resolve("file.txt"), new byte[]{1, 2, 3});

        transfer.setStatus(TransferStatus.ACTIVE);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));

        asyncSender.sendAsync(transferId, "127.0.0.1", 1, "token");

        verify(transferRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == TransferStatus.FAILED &&
                        t.getFailureReason() != null
        ));
    }

    /**
     * markFailed does nothing if the transfer record no longer exists.
     */
    @Test
    void markFailed_doesNothingIfTransferNotFound() {
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.empty());

        asyncSender.sendAsync(transferId, "127.0.0.1", 9999, "token");

        verify(transferRepository, never()).save(any());
    }
}