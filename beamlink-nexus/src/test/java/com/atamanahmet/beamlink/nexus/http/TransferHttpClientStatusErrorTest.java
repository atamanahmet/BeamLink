package com.atamanahmet.beamlink.nexus.http;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.nexus.exception.ErrorReason;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
import com.atamanahmet.beamlink.nexus.service.FileTransferErrorRule;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpConnectTimeoutException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferHttpClientStatusErrorTest {

    /**
     * Register connect timeout on a LAN IP stays CONNECT_TIMEOUT.
     */
    @Test
    void registerConnectTimeoutOnLanIsConnectTimeout() throws Exception {
        HttpSender httpSender = mock(HttpSender.class);
        when(httpSender.sendPinned(any(), eq("aaaa bbbb")))
                .thenThrow(new HttpConnectTimeoutException("connect"));

        NexusService nexusService = mock(NexusService.class);
        when(nexusService.getNexusId()).thenReturn(UUID.randomUUID());
        when(nexusService.getNexusIp()).thenReturn("10.0.0.1");
        when(nexusService.getNexusPort()).thenReturn(7473);
        when(nexusService.getPeerPublicToken()).thenReturn("tok");

        DeviceService deviceService = mock(DeviceService.class);
        Device device = new Device();
        device.setTlsFingerprint("aaaa bbbb");
        device.setTlsPort(9000);
        device.setPort(9000);
        when(deviceService.findByLanAddress("192.168.1.5", 9000)).thenReturn(Optional.of(device));

        TransferHttpClient client = new TransferHttpClient(
                httpSender, new ObjectMapper(), nexusService, deviceService);

        InitiateTransferRequest request = new InitiateTransferRequest();
        request.setTargetIp("192.168.1.5");
        request.setTargetPort(9000);

        assertThatThrownBy(() -> client.registerTransfer(
                        request,
                        UUID.randomUUID(),
                        "a.bin",
                        10L))
                .isInstanceOf(FileTransferException.class)
                .satisfies(ex -> assertThat(FileTransferErrorRule.reason(ex))
                        .isEqualTo(ErrorReason.CONNECT_TIMEOUT));
    }
}
