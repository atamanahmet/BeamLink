package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.TransferProperties;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.http.TransferHttpClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkReceiverEmptyFileTest {

    @Test
    void prepareReceiveCompletesZeroByteFile() {
        StorageService storageService = mock(StorageService.class);
        when(storageService.usableBytes()).thenReturn(10_000_000_000L);
        when(storageService.resolvePartialPath(any())).thenReturn(Path.of("empty.bin.part"));
        DiskReserveProvider provider = mock(DiskReserveProvider.class);
        when(provider.getBytes()).thenReturn(1073741824L);

        ChunkReceiverService receiver = new ChunkReceiverService(
                mock(FileTransferService.class),
                storageService,
                mock(TransferLogService.class),
                mock(DirectoryTransferService.class),
                mock(BatchTransferService.class),
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                mock(TransferProperties.class),
                provider);

        FileTransfer transfer = FileTransfer.initiate(
                UUID.randomUUID(), UUID.randomUUID(), null, "empty.bin", null, 0L);
        transfer.setStatus(TransferStatus.ACTIVE);

        receiver.prepareReceive(transfer);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(storageService).allocatePartialFile(any(), eq(0L));
        verify(storageService).moveToFinalLocation(transfer);
    }
}
