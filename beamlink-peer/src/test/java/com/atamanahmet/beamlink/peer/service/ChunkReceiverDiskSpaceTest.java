package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.TransferProperties;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.exception.InsufficientDiskException;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkReceiverDiskSpaceTest {

    @Test
    void prepareReceiveRejectsWhenDiskFull() {
        StorageService storageService = mock(StorageService.class);
        when(storageService.usableBytes()).thenReturn(100L);
        DiskReserveProvider provider = mock(DiskReserveProvider.class);
        when(provider.getBytes()).thenReturn(1073741824L);

        ChunkReceiverService receiver = new ChunkReceiverService(
                mock(FileTransferService.class),
                mock(DirectoryTransferService.class),
                mock(BatchTransferService.class),
                mock(TransferLogService.class),
                mock(DeviceService.class),
                storageService,
                mock(ReceiveFileChannels.class),
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                mock(TransferProperties.class),
                provider);

        FileTransfer transfer = FileTransfer.initiate(
                UUID.randomUUID(), UUID.randomUUID(), null, "a.bin", null, 1024L);

        assertThatThrownBy(() -> receiver.prepareReceive(transfer))
                .isInstanceOf(InsufficientDiskException.class);
        verify(storageService, never()).allocatePartialFile(any(), anyLong());
    }
}
