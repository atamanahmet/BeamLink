package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.config.StorageConfig;
import com.atamanahmet.beamlink.peer.config.TransferProperties;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.exception.InvalidRequestException;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.util.FileChecksum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PoisonedPartialCleanupTest {

    @TempDir
    Path temp;

    private Path uploads;
    private Path outsideFile;
    private StorageService storageService;
    private FileTransferService fileTransferService;
    private ChunkReceiverService receiver;
    private TransferExpiryJob expiryJob;

    @BeforeEach
    void setUp() throws Exception {
        uploads = temp.resolve("uploads");
        Path outsideDir = temp.resolve("outside");
        Files.createDirectories(outsideDir);
        outsideFile = outsideDir.resolve("poison.bin");
        Files.writeString(outsideFile, "keep-me", StandardCharsets.UTF_8);

        SettingsService settings = mock(SettingsService.class);
        when(settings.get(SettingKey.STORAGE_UPLOAD_DIR)).thenReturn(Optional.of(uploads.toString()));
        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getDefaultUploadsDir()).thenReturn(uploads);
        storageService = new StorageService(storageConfig, settings);
        storageService.init();

        fileTransferService = mock(FileTransferService.class);
        when(fileTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileTransferService.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        DiskReserveProvider reserve = mock(DiskReserveProvider.class);
        when(reserve.getBytes()).thenReturn(0L);
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getDeviceId()).thenReturn(UUID.randomUUID());
        when(deviceService.getDeviceName()).thenReturn("peer");

        TransferProperties props = mock(TransferProperties.class);
        when(props.getChunkSizeBytes()).thenReturn(1024 * 1024);

        receiver = new ChunkReceiverService(
                fileTransferService,
                mock(DirectoryTransferService.class),
                mock(BatchTransferService.class),
                mock(TransferLogService.class),
                deviceService,
                storageService,
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                props,
                reserve);

        DeviceConfig deviceConfig = mock(DeviceConfig.class);
        expiryJob = new TransferExpiryJob(
                deviceConfig, fileTransferService, storageService, receiver);
    }

    /**
     * Builds a FileTransfer with a chosen stored filePath.
     */
    static FileTransfer transferWithPath(String fileName, String filePath, long fileSize) {
        FileTransfer ft = FileTransfer.initiate(
                UUID.randomUUID(), UUID.randomUUID(), null, fileName, filePath, fileSize);
        ft.setStatus(TransferStatus.ACTIVE);
        ft.setExpiresAt(Instant.now().minusSeconds(60));
        return ft;
    }

    @Test
    void cancelSkipsPoisonedDeleteAndMarksCancelled() throws Exception {
        FileTransfer ft = transferWithPath("a.bin", outsideFile.toString(), 1L);
        when(fileTransferService.findByTransferId(ft.getTransferId())).thenReturn(Optional.of(ft));

        receiver.cancelReceive(ft.getTransferId(), ft.getSourceDeviceId());

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(outsideFile).exists();
        assertThat(Files.readString(outsideFile)).isEqualTo("keep-me");
    }

    @Test
    void failSkipsPoisonedDeleteAndMarksFailed() throws Exception {
        FileTransfer ft = transferWithPath("a.bin", outsideFile.toString(), 1L);
        when(fileTransferService.findByTransferId(ft.getTransferId())).thenReturn(Optional.of(ft));

        receiver.failReceive(ft.getTransferId(), "peer-fail", ft.getSourceDeviceId());

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(outsideFile).exists();
        assertThat(Files.readString(outsideFile)).isEqualTo("keep-me");
    }

    @Test
    void sizeMismatchSkipsPoisonedDeleteAndMarksFailed() throws Exception {
        FileTransfer ft = transferWithPath("a.bin", outsideFile.toString(), 1L);

        ReflectionTestUtils.invokeMethod(
                receiver, "markSizeMismatch", ft, ft.getTransferId());

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(outsideFile).exists();
        assertThat(Files.readString(outsideFile)).isEqualTo("keep-me");
    }

    @Test
    void expiryContinuesAfterPoisonedRow() throws Exception {
        FileTransfer poisoned = transferWithPath("poison.bin", outsideFile.toString(), 1L);
        FileTransfer normal = transferWithPath("ok.bin", null, 1L);
        when(fileTransferService.findByStatusAndExpiresAtBefore(
                any(TransferStatus.class), any(Instant.class)))
                .thenReturn(List.of(poisoned, normal))
                .thenReturn(List.of());

        expiryJob.expireStaleTransfers();

        assertThat(poisoned.getStatus()).isEqualTo(TransferStatus.EXPIRED);
        assertThat(normal.getStatus()).isEqualTo(TransferStatus.EXPIRED);
        assertThat(outsideFile).exists();
        assertThat(Files.readString(outsideFile)).isEqualTo("keep-me");
    }

    @Test
    void chunkOnPoisonedRowStillRejectsWithoutTouchingOutsideFile() throws Exception {
        FileTransfer ft = transferWithPath("a.bin", outsideFile.toString(), 10L);
        when(fileTransferService.findByTransferId(ft.getTransferId())).thenReturn(Optional.of(ft));
        long before = Files.size(outsideFile);
        byte[] body = new byte[] {1};
        String sha = FileChecksum.sha256Hex(body);

        assertThatThrownBy(() -> receiver.receiveChunk(
                ft.getTransferId(),
                0L,
                1L,
                new ByteArrayInputStream(body),
                sha,
                ft.getSourceDeviceId()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(ex -> ((InvalidRequestException) ex).getCode())
                .isIn("PATH_ESCAPE", "PATH_EQUALS_ROOT");

        assertThat(outsideFile).exists();
        assertThat(Files.size(outsideFile)).isEqualTo(before);
        assertThat(Files.readString(outsideFile)).isEqualTo("keep-me");
    }
}
