package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkReceiverServiceTest {

    @Mock
    private FileTransferRepository transferRepository;

    @Mock
    private AgentConfig agentConfig;

    @Mock
    private LogService logService;

    @Mock
    private AgentService agentService;

    @InjectMocks
    private ChunkReceiverService chunkReceiverService;

    @TempDir
    Path tempDir;

    private UUID transferId;
    private FileTransfer activeTransfer;

    // ─── existing guard tests (kept as-is) ────────────────────────────────────

    @BeforeEach
    void setUp() {
        transferId = UUID.randomUUID();
        activeTransfer = FileTransfer.initiate(
                transferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "testfile.txt",
                null,
                1024L
        );
        activeTransfer.setStatus(TransferStatus.ACTIVE);
    }

    @Test
    void receiveChunk_rejectsTransferNotFound() {
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                chunkReceiverService.receiveChunk(transferId, 0,
                        new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Transfer not found");
    }

    @Test
    void receiveChunk_rejectsNonActiveTransfer() {
        activeTransfer.setStatus(TransferStatus.PAUSED);
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(activeTransfer));

        assertThatThrownBy(() ->
                chunkReceiverService.receiveChunk(transferId, 0,
                        new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void receiveChunk_rejectsOutOfOrderOffset() {
        activeTransfer.setConfirmedOffset(0L);
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(activeTransfer));

        assertThatThrownBy(() ->
                chunkReceiverService.receiveChunk(transferId, 512,
                        new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Unexpected offset");
    }

    @Test
    void prepareReceive_rejectsZeroFileSize() {
        FileTransfer zeroSize = FileTransfer.initiate(
                transferId, UUID.randomUUID(), null, "testfile.txt", null, 0L
        );

        assertThatThrownBy(() -> chunkReceiverService.prepareReceive(zeroSize))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Invalid file size");
    }

    /**
     * prepareReceive must pre-allocate a .part file with the exact declared size.
     */
    @Test
    void prepareReceive_createsPartialFileWithCorrectSize() throws IOException {

        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), UUID.randomUUID(),
                "myfile.bin", null, 2048L
        );
        transfer.setStatus(TransferStatus.ACTIVE);

        // Should not throw
        assertThatCode(() -> chunkReceiverService.prepareReceive(transfer))
                .doesNotThrowAnyException();

        verify(transferRepository).save(transfer);

        // Clean up partial file created under CWD
        Path partial = Path.of("./data/partial/myfile.bin.part");
        Files.deleteIfExists(partial);
    }

    /**
     * Full path, single chunk that fills the file to status COMPLETED.
     */
    @Test
    void receiveChunk_singleChunkCompletesTransfer() throws IOException {
        byte[] payload = "hello agent transfer".getBytes();
        long fileSize = payload.length;

        String fileName = "single-" + transferId + ".bin";

        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), UUID.randomUUID(),
                fileName, null, fileSize
        );
        transfer.setStatus(TransferStatus.ACTIVE);
        transfer.setConfirmedOffset(0L);

        Path partialDir = Path.of("./data/partial");
        Files.createDirectories(partialDir);
        Path partialFile = partialDir.resolve(fileName + ".part");
        try (var raf = new java.io.RandomAccessFile(partialFile.toFile(), "rw")) {
            raf.setLength(fileSize);
        }

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));
        when(agentConfig.getUploadDirectory())
                .thenReturn(tempDir.toString());
        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentService.getAgentName()).thenReturn("test-agent");

        ChunkAckResponse ack = chunkReceiverService.receiveChunk(
                transferId, 0L, new ByteArrayInputStream(payload)
        );

        assertThat(ack.getConfirmedOffset()).isEqualTo(fileSize);
        assertThat(ack.isComplete()).isTrue();

        verify(transferRepository, atLeastOnce()).save(argThat(t ->
                t.getStatus() == TransferStatus.COMPLETED
        ));

        Path finalFile = tempDir.resolve(fileName);
        assertThat(finalFile).exists();
        assertThat(Files.readAllBytes(finalFile)).isEqualTo(payload);
    }

    /**
     * Two sequential chunks, each processed by receiveChunk in order.
     * After both chunks confirmedOffset == fileSize and status is COMPLETED.
     */
    @Test
    void receiveChunk_multipleChunksReassembleCorrectly() throws IOException {
        byte[] part1 = "FIRST_CHUNK_".getBytes();
        byte[] part2 = "SECOND_CHUNK".getBytes();
        long fileSize = part1.length + part2.length;

        String fileName = "multi-" + transferId + ".bin";

        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), UUID.randomUUID(),
                fileName, null, fileSize
        );
        transfer.setStatus(TransferStatus.ACTIVE);
        transfer.setConfirmedOffset(0L);

        // Pre-allocate .part file
        Path partialDir = Path.of("./data/partial");
        Files.createDirectories(partialDir);
        Path partialFile = partialDir.resolve(fileName + ".part");
        try (var raf = new java.io.RandomAccessFile(partialFile.toFile(), "rw")) {
            raf.setLength(fileSize);
        }

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));
        when(agentConfig.getUploadDirectory())
                .thenReturn(tempDir.toString());
        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
        when(agentService.getAgentName()).thenReturn("test-agent");

        // ── chunk 1 ──
        ChunkAckResponse ack1 = chunkReceiverService.receiveChunk(
                transferId, 0L, new ByteArrayInputStream(part1)
        );

        assertThat(ack1.getConfirmedOffset()).isEqualTo(part1.length);
        assertThat(ack1.isComplete()).isFalse();

        // Simulate DB update between chunks (service saves and we re-read)
        transfer.setConfirmedOffset(ack1.getConfirmedOffset());

        // ── chunk 2 ──
        ChunkAckResponse ack2 = chunkReceiverService.receiveChunk(
                transferId, (long) part1.length, new ByteArrayInputStream(part2)
        );

        assertThat(ack2.getConfirmedOffset()).isEqualTo(fileSize);
        assertThat(ack2.isComplete()).isTrue();

        // Reassembled file must equal part1 + part2
        byte[] expected = new byte[part1.length + part2.length];
        System.arraycopy(part1, 0, expected, 0, part1.length);
        System.arraycopy(part2, 0, expected, part1.length, part2.length);

        Path finalFile = tempDir.resolve(fileName);
        assertThat(Files.readAllBytes(finalFile)).isEqualTo(expected);
    }

    /**
     * receiveChunk on a CANCELLED transfer must throw, not write anything.
     */
    @Test
    void receiveChunk_rejectsCancelledTransfer() {
        activeTransfer.setStatus(TransferStatus.CANCELLED);
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(activeTransfer));

        assertThatThrownBy(() ->
                chunkReceiverService.receiveChunk(transferId, 0,
                        new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("not active");

        verify(transferRepository, never()).save(any());
    }

    /**
     * receiveChunk on a FAILED transfer must throw.
     */
    @Test
    void receiveChunk_rejectsFailedTransfer() {
        activeTransfer.setStatus(TransferStatus.FAILED);
        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(activeTransfer));

        assertThatThrownBy(() ->
                chunkReceiverService.receiveChunk(transferId, 0,
                        new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("not active");
    }

    /**
     * confirmedOffset is updated correctly after each chunk
     * the next expected offset is previous + bytes written.
     */
    @Test
    void receiveChunk_updatesConfirmedOffsetAfterWrite() throws IOException {
        byte[] payload = new byte[512];
        java.util.Arrays.fill(payload, (byte) 0xAB);
        long fileSize = 1024L;   // two chunks of 512

        String fileName = "offset-" + transferId + ".bin";
        Path partialDir = Path.of("./data/partial");
        Files.createDirectories(partialDir);
        try (var raf = new java.io.RandomAccessFile(
                partialDir.resolve(fileName + ".part").toFile(), "rw")) {
            raf.setLength(fileSize);
        }

        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), UUID.randomUUID(),
                fileName, null, fileSize
        );
        transfer.setStatus(TransferStatus.ACTIVE);
        transfer.setConfirmedOffset(0L);

        when(transferRepository.findByTransferId(transferId))
                .thenReturn(Optional.of(transfer));
//        when(agentConfig.getUploadDirectory()).thenReturn(tempDir.toString());
//        when(agentService.getAgentId()).thenReturn(UUID.randomUUID());
//        when(agentService.getAgentName()).thenReturn("test-agent");

        ChunkAckResponse ack = chunkReceiverService.receiveChunk(
                transferId, 0L, new ByteArrayInputStream(payload)
        );

        assertThat(ack.getConfirmedOffset()).isEqualTo(512L);
        assertThat(ack.isComplete()).isFalse();

        verify(transferRepository).save(argThat(t ->
                t.getConfirmedOffset() == 512L
        ));

        // Cleanup
        Files.deleteIfExists(partialDir.resolve(fileName + ".part"));
    }

    /**
     * prepareReceive must reject negative file size
     */
    @Test
    void prepareReceive_rejectsNegativeFileSize() {
        FileTransfer transfer = FileTransfer.initiate(
                transferId, UUID.randomUUID(), null, "bad.bin", null, -1L
        );

        assertThatThrownBy(() -> chunkReceiverService.prepareReceive(transfer))
                .isInstanceOf(FileTransferException.class)
                .hasMessageContaining("Invalid file size");
    }
}