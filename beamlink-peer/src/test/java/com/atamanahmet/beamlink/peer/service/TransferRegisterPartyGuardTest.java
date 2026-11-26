package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.StorageConfig;
import com.atamanahmet.beamlink.peer.config.TransferProperties;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.peer.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.peer.exception.ForbiddenException;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferRegisterPartyGuardTest {

    private final UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID stranger = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @TempDir
    Path temp;

    private Path uploads;
    private FileTransferService fileTransferService;
    private DirectoryTransferService directoryTransferService;
    private BatchTransferService batchTransferService;
    private StorageService storageService;
    private ChunkReceiverService receiver;

    @BeforeEach
    void setUp() {
        uploads = temp.resolve("uploads");
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(SettingKey.STORAGE_UPLOAD_DIR)).thenReturn(Optional.of(uploads.toString()));
        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getDefaultUploadsDir()).thenReturn(uploads);
        storageService = new StorageService(storageConfig, settings);
        storageService.init();

        fileTransferService = mock(FileTransferService.class);
        when(fileTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileTransferService.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        directoryTransferService = mock(DirectoryTransferService.class);
        when(directoryTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        batchTransferService = mock(BatchTransferService.class);
        when(batchTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DiskReserveProvider reserve = mock(DiskReserveProvider.class);
        when(reserve.getBytes()).thenReturn(0L);
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getDeviceId()).thenReturn(source);
        when(deviceService.getDeviceName()).thenReturn("peer");

        receiver = new ChunkReceiverService(
                fileTransferService,
                directoryTransferService,
                batchTransferService,
                mock(TransferLogService.class),
                deviceService,
                storageService,
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                mock(TransferProperties.class),
                reserve);
    }

    @Test
    void prepareReceiveRejectsStrangerWritesNothing() throws Exception {
        FileTransfer transfer = FileTransfer.initiate(
                UUID.randomUUID(), source, null, "a.bin", null, 1L);
        transfer.setStatus(TransferStatus.ACTIVE);

        assertThatThrownBy(() -> receiver.prepareReceive(transfer, stranger))
                .isInstanceOf(ForbiddenException.class);
        verify(fileTransferService, never()).save(any());
        assertRootsClean();
    }

    @Test
    void prepareReceiveAllowsSource() throws Exception {
        FileTransfer transfer = FileTransfer.initiate(
                UUID.randomUUID(), source, null, "a.bin", null, 1L);
        transfer.setStatus(TransferStatus.ACTIVE);

        receiver.prepareReceive(transfer, source);

        verify(fileTransferService).save(transfer);
        assertThat(transfer.getFilePath()).isNotBlank();
        assertThat(Path.of(transfer.getFilePath())).exists();
    }

    @Test
    void prepareReceiveDirectoryRejectsStrangerWritesNothing() throws Exception {
        ReceiveDirectoryRequest request = directoryRequest();

        assertThatThrownBy(() -> receiver.prepareReceiveDirectory(request, stranger))
                .isInstanceOf(ForbiddenException.class);
        verify(directoryTransferService, never()).save(any());
        verify(fileTransferService, never()).saveAll(any());
        assertRootsClean();
    }

    @Test
    void prepareReceiveDirectoryAllowsSource() throws Exception {
        ReceiveDirectoryRequest request = directoryRequest();

        receiver.prepareReceiveDirectory(request, source);

        verify(directoryTransferService).save(any());
        verify(fileTransferService).saveAll(any());
    }

    @Test
    void prepareReceiveBatchRejectsStrangerWritesNothing() throws Exception {
        ReceiveBatchRequest request = batchRequest();

        assertThatThrownBy(() -> receiver.prepareReceiveBatch(request, stranger))
                .isInstanceOf(ForbiddenException.class);
        verify(batchTransferService, never()).save(any());
        verify(fileTransferService, never()).saveAll(any());
        assertRootsClean();
    }

    @Test
    void prepareReceiveBatchAllowsSource() throws Exception {
        ReceiveBatchRequest request = batchRequest();

        receiver.prepareReceiveBatch(request, source);

        verify(batchTransferService).save(any());
        verify(fileTransferService).saveAll(any());
    }

    private ReceiveDirectoryRequest directoryRequest() {
        ReceiveDirectoryRequest request = new ReceiveDirectoryRequest();
        request.setDirectoryTransferId(UUID.randomUUID());
        request.setSourceDeviceId(source);
        request.setDirectoryName("okdir");
        request.setTotalFiles(1);
        request.setTotalSize(1);
        request.setEmptyDirectories(List.of());
        ReceiveDirectoryRequest.FileEntry entry = new ReceiveDirectoryRequest.FileEntry();
        entry.setTransferId(UUID.randomUUID());
        entry.setFileName("a.bin");
        entry.setRelativePath("a.bin");
        entry.setFileSize(1L);
        request.setFiles(List.of(entry));
        return request;
    }

    private ReceiveBatchRequest batchRequest() {
        ReceiveBatchRequest request = new ReceiveBatchRequest();
        request.setBatchTransferId(UUID.randomUUID());
        request.setSourceDeviceId(source);
        request.setTotalFiles(1);
        request.setTotalSize(1);
        ReceiveBatchRequest.FileEntry entry = new ReceiveBatchRequest.FileEntry();
        entry.setTransferId(UUID.randomUUID());
        entry.setFileName("a.bin");
        entry.setFileSize(1L);
        request.setFiles(List.of(entry));
        return request;
    }

    private void assertRootsClean() throws Exception {
        Path partial = uploads.resolve(".beamlink-partial");
        assertThat(uploads).exists();
        assertThat(partial).exists();
        try (Stream<Path> up = Files.list(uploads)) {
            assertThat(up.map(p -> p.getFileName().toString()).toList())
                    .containsExactly(".beamlink-partial");
        }
        try (Stream<Path> parts = Files.list(partial)) {
            assertThat(parts.toList()).isEmpty();
        }
    }
}
