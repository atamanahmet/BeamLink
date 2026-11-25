package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.StorageConfig;
import com.atamanahmet.beamlink.nexus.config.TransferProperties;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.nexus.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.http.TransferHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
import static org.mockito.Mockito.when;

class ReceivePathWiringTest {

    @TempDir
    Path temp;

    private Path uploads;
    private ChunkReceiverService receiver;

    @BeforeEach
    void setUp() {
        uploads = temp.resolve("uploads");
        SettingsService settings = mock(SettingsService.class);
        when(settings.get(SettingKey.STORAGE_UPLOAD_DIR)).thenReturn(Optional.of(uploads.toString()));
        StorageConfig storageConfig = mock(StorageConfig.class);
        when(storageConfig.getDefaultUploadsDir()).thenReturn(uploads);
        StorageService storageService = new StorageService(storageConfig, settings);
        storageService.init();

        DiskReserveProvider reserve = mock(DiskReserveProvider.class);
        when(reserve.getBytes()).thenReturn(0L);

        FileTransferService fileTransferService = mock(FileTransferService.class);
        when(fileTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileTransferService.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        DirectoryTransferService directoryTransferService = mock(DirectoryTransferService.class);
        when(directoryTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchTransferService batchTransferService = mock(BatchTransferService.class);
        when(batchTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        receiver = new ChunkReceiverService(
                fileTransferService,
                storageService,
                mock(TransferLogService.class),
                directoryTransferService,
                batchTransferService,
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                mock(TransferProperties.class),
                reserve);
    }

    static Stream<String> badNames() {
        return Stream.of(
                "..",
                "/abs",
                "C:\\x",
                "a\u0000b",
                "CON",
                "a\\b");
    }

    @ParameterizedTest(name = "receive fileName={0}")
    @MethodSource("badNames")
    void receiveRejectsBadFileName(String fileName) throws Exception {
        UUID src = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                UUID.randomUUID(), src, null, fileName, null, 1L);
        transfer.setStatus(TransferStatus.ACTIVE);
        assertThatThrownBy(() -> receiver.prepareReceive(transfer, src))
                .isInstanceOf(InvalidRequestException.class);
        assertRootsClean();
    }

    @ParameterizedTest(name = "receive-batch fileName={0}")
    @MethodSource("badNames")
    void receiveBatchRejectsBadFileName(String fileName) throws Exception {
        UUID src = UUID.randomUUID();
        ReceiveBatchRequest request = new ReceiveBatchRequest();
        request.setBatchTransferId(UUID.randomUUID());
        request.setSourceDeviceId(src);
        request.setTotalFiles(1);
        request.setTotalSize(1);
        ReceiveBatchRequest.FileEntry entry = new ReceiveBatchRequest.FileEntry();
        entry.setTransferId(UUID.randomUUID());
        entry.setFileName(fileName);
        entry.setFileSize(1L);
        request.setFiles(List.of(entry));
        assertThatThrownBy(() -> receiver.prepareReceiveBatch(request, src))
                .isInstanceOf(InvalidRequestException.class);
        assertRootsClean();
    }

    @ParameterizedTest(name = "receive-directory directoryName={0}")
    @MethodSource("badNames")
    void receiveDirectoryRejectsBadDirectoryName(String directoryName) throws Exception {
        postDirectory(directoryName, "file.txt", "file.txt");
        assertRootsClean();
    }

    @Test
    void receiveDirectoryRejectsBackslashRelativePath() throws Exception {
        postDirectory("okdir", "file.txt", "a\\b.txt");
        assertRootsClean();
    }

    @ParameterizedTest(name = "receive-directory fileName={0}")
    @MethodSource("badNames")
    void receiveDirectoryRejectsBadFileName(String fileName) throws Exception {
        postDirectory("okdir", fileName, "ok.txt");
        assertRootsClean();
    }

    private void postDirectory(String directoryName, String fileName, String relativePath) {
        UUID src = UUID.randomUUID();
        ReceiveDirectoryRequest request = new ReceiveDirectoryRequest();
        request.setDirectoryTransferId(UUID.randomUUID());
        request.setSourceDeviceId(src);
        request.setDirectoryName(directoryName);
        request.setTotalFiles(1);
        request.setTotalSize(1);
        request.setEmptyDirectories(List.of());
        ReceiveDirectoryRequest.FileEntry entry = new ReceiveDirectoryRequest.FileEntry();
        entry.setTransferId(UUID.randomUUID());
        entry.setFileName(fileName);
        entry.setRelativePath(relativePath);
        entry.setFileSize(1L);
        request.setFiles(List.of(entry));
        assertThatThrownBy(() -> receiver.prepareReceiveDirectory(request, src))
                .isInstanceOf(InvalidRequestException.class);
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
        Path parent = uploads.getParent();
        if (parent != null) {
            try (Stream<Path> siblings = Files.list(parent)) {
                assertThat(siblings.map(p -> p.getFileName().toString()).toList())
                        .containsExactly("uploads");
            }
        }
    }
}
