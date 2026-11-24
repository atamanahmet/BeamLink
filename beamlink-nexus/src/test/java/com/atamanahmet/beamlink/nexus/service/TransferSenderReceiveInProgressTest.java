package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.TransferProperties;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.exception.TransferErrorCodes;
import com.atamanahmet.beamlink.nexus.http.TransferHttpClient;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferSenderReceiveInProgressTest {

    @TempDir
    Path tempDir;

    /**
     * Real 409 RECEIVE_IN_PROGRESS body fills code, retries, then pauses SOURCE.
     */
    @Test
    @SuppressWarnings("unchecked")
    void receiveInProgressPausesSource() throws Exception {
        Path file = tempDir.resolve("a.bin");
        Files.write(file, new byte[] {1});

        UUID transferId = UUID.randomUUID();
        UUID targetDeviceId = UUID.randomUUID();
        FileTransfer transfer = FileTransfer.initiate(
                transferId,
                UUID.randomUUID(),
                targetDeviceId,
                "a.bin",
                file.toString(),
                1L);
        transfer.setStatus(TransferStatus.ACTIVE);
        transfer.setMaxRetries(1);
        transfer.setTargetIp("10.0.0.2");

        FileTransferService fileTransferService = mock(FileTransferService.class);
        when(fileTransferService.findByTransferId(transferId)).thenReturn(Optional.of(transfer));

        TransferHttpClient http = mock(TransferHttpClient.class);
        when(http.baseUrl(targetDeviceId, "10.0.0.2")).thenReturn("https://10.0.0.2:9000");
        when(http.resolveStartOffset(eq(targetDeviceId), eq("10.0.0.2"), eq(transferId), eq(0L)))
                .thenReturn(0L);

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(409);
        when(response.body()).thenReturn(
                "{\"code\":\"" + TransferErrorCodes.RECEIVE_IN_PROGRESS + "\",\"detail\":\"busy\"}");
        when(http.exchange(any(), eq(targetDeviceId))).thenReturn(response);

        TransferSpeedLimiter limiter = mock(TransferSpeedLimiter.class);
        when(limiter.acquire(any(), any(), anyInt())).thenReturn(0L);

        TransferSender sender = new TransferSender(
                fileTransferService,
                new ObjectMapper(),
                http,
                mock(TransferSideResolver.class),
                limiter,
                new TransferProperties());

        sender.sendBlocking(transferId, "10.0.0.2", 9000);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PAUSED);
        assertThat(transfer.getPausedBy()).isEqualTo(PauseOwner.SOURCE);
    }
}
