package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.config.TransferProperties;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.exception.ForbiddenException;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.util.FileChecksum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferPartyServiceGuardTest {

    private final UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID target = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID stranger = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @TempDir
    Path temp;

    private FileTransferService fileTransferService;
    private ReceiveFileChannels receiveFileChannels;
    private StorageService storageService;
    private ChunkReceiverService receiver;
    private TransferSenderService sender;
    private TransferManagementService management;

    @BeforeEach
    void setUp() {
        fileTransferService = mock(FileTransferService.class);
        when(fileTransferService.save(any())).thenAnswer(inv -> inv.getArgument(0));
        receiveFileChannels = mock(ReceiveFileChannels.class);
        storageService = mock(StorageService.class);
        when(storageService.usableBytes()).thenReturn(10_000_000_000L);
        when(storageService.storedPartialPath(any())).thenAnswer(inv -> {
            FileTransfer ft = inv.getArgument(0);
            return Path.of(ft.getFilePath());
        });

        DiskReserveProvider reserve = mock(DiskReserveProvider.class);
        when(reserve.getBytes()).thenReturn(0L);
        TransferProperties props = mock(TransferProperties.class);
        when(props.getChunkSizeBytes()).thenReturn(1024 * 1024);
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getDeviceId()).thenReturn(source);
        when(deviceService.getDeviceName()).thenReturn("peer");

        receiver = new ChunkReceiverService(
                fileTransferService,
                mock(DirectoryTransferService.class),
                mock(BatchTransferService.class),
                mock(TransferLogService.class),
                deviceService,
                storageService,
                receiveFileChannels,
                mock(ReceiveStallTracker.class),
                mock(TransferHttpClient.class),
                props,
                reserve);

        sender = new TransferSenderService(
                fileTransferService,
                mock(DeviceConfig.class),
                mock(TransferHttpClient.class),
                deviceService);

        TransferSideResolver sideResolver = mock(TransferSideResolver.class);
        when(sideResolver.resolve(any(), any(), any())).thenReturn(PauseOwner.TARGET);
        management = new TransferManagementService(
                sender,
                mock(BatchSenderService.class),
                mock(DirectorySenderService.class),
                mock(BatchTransferService.class),
                mock(DirectoryTransferService.class),
                fileTransferService,
                mock(TransferSender.class),
                mock(TransferHttpClient.class),
                mock(DeviceResumePolicyHolder.class),
                sideResolver,
                mock(TransferSpeedLimiter.class),
                receiver,
                mock(ReceiveStallTracker.class),
                storageService,
                reserve);
    }

    private FileTransfer receiverRow(UUID id) {
        FileTransfer ft = FileTransfer.initiate(id, source, null, "a.bin", null, 10L);
        ft.setStatus(TransferStatus.ACTIVE);
        ft.setFilePath(temp.resolve(id + ".part").toString());
        return ft;
    }

    private FileTransfer senderRow(UUID id) {
        FileTransfer ft = FileTransfer.initiate(
                id, source, target, "a.bin", temp.resolve("a.bin").toString(), 10L);
        ft.setStatus(TransferStatus.ACTIVE);
        return ft;
    }

    @Test
    void receiveChunkRejectsStrangerAndWritesNothing() throws Exception {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));
        byte[] body = new byte[] {1};
        String sha = FileChecksum.sha256Hex(body);

        assertThatThrownBy(() -> receiver.receiveChunk(
                id, 0L, 1L, new ByteArrayInputStream(body), sha, stranger))
                .isInstanceOf(ForbiddenException.class);
        verify(receiveFileChannels, never()).write(any(), any(), anyLong(), any());
        assertThat(ft.getStatus()).isEqualTo(TransferStatus.ACTIVE);
        assertThat(ft.getConfirmedOffset()).isZero();
    }

    @Test
    void receiveChunkAllowsSource() throws Exception {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));
        when(receiveFileChannels.write(any(), any(), anyLong(), any())).thenReturn(1L);
        byte[] body = new byte[] {1};
        String sha = FileChecksum.sha256Hex(body);

        var ack = receiver.receiveChunk(id, 0L, 1L, new ByteArrayInputStream(body), sha, source);

        assertThat(ack.getConfirmedOffset()).isEqualTo(1L);
        verify(receiveFileChannels).write(any(), any(), anyLong(), any());
    }

    @Test
    void getConfirmedOffsetRejectsStranger() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThatThrownBy(() -> receiver.getConfirmedOffset(id, stranger))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ft.getConfirmedOffset()).isZero();
    }

    @Test
    void getConfirmedOffsetAllowsSource() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        ft.setConfirmedOffset(4L);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThat(receiver.getConfirmedOffset(id, source)).isEqualTo(4L);
    }

    @Test
    void cancelReceiveRejectsStrangerLeavesRow() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThatThrownBy(() -> receiver.cancelReceive(id, stranger))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ft.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void cancelReceiveAllowsSourceOnReceiverRow() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        receiver.cancelReceive(id, source);

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.CANCELLED);
    }

    @Test
    void applyCancelNotifyAllowsTargetOnSenderRow() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = senderRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        sender.applyCancelNotify(id, target);

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.CANCELLED);
    }

    @Test
    void applyCancelNotifyRejectsStranger() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = senderRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThatThrownBy(() -> sender.applyCancelNotify(id, stranger))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ft.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void uiCancelWithoutCallerStillWorks() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = senderRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        sender.cancel(id);

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.CANCELLED);
    }

    @Test
    void failReceiveRejectsStranger() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThatThrownBy(() -> receiver.failReceive(id, "x", stranger))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ft.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void failReceiveAllowsSourceOnReceiverRow() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        receiver.failReceive(id, "x", source);

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void applyFailNotifyRejectsStranger() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = senderRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThatThrownBy(() -> sender.applyFailNotify(id, "x", stranger))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ft.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void applyFailNotifyAllowsTargetOnSenderRow() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = senderRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        sender.applyFailNotify(id, "x", target);

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void applyPauseNotifyRejectsStranger() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThatThrownBy(() -> management.applyPauseNotify(id, PauseOwner.SOURCE, stranger))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ft.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }

    @Test
    void applyPauseNotifyAllowsSourceOnReceiverRow() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        management.applyPauseNotify(id, PauseOwner.SOURCE, source);

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.PAUSED);
    }

    @Test
    void applyResumeNotifyRejectsStranger() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        ft.setStatus(TransferStatus.PAUSED);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        assertThatThrownBy(() -> management.applyResumeNotify(id, stranger))
                .isInstanceOf(ForbiddenException.class);
        assertThat(ft.getStatus()).isEqualTo(TransferStatus.PAUSED);
    }

    @Test
    void applyResumeNotifyAllowsSourceOnReceiverRow() {
        UUID id = UUID.randomUUID();
        FileTransfer ft = receiverRow(id);
        ft.setStatus(TransferStatus.PAUSED);
        when(fileTransferService.findByTransferId(id)).thenReturn(Optional.of(ft));

        management.applyResumeNotify(id, source);

        assertThat(ft.getStatus()).isEqualTo(TransferStatus.ACTIVE);
    }
}
