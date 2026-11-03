package com.atamanahmet.beamlink.peer.http;

import com.atamanahmet.beamlink.peer.domain.Peer;
import com.atamanahmet.beamlink.peer.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.peer.exception.ErrorReason;
import com.atamanahmet.beamlink.peer.exception.FileTransferException;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.atamanahmet.beamlink.peer.service.FileTransferErrorRule;
import com.atamanahmet.beamlink.peer.service.PeerCacheService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferHttpClientStatusErrorTest {

    @Test
    @SuppressWarnings("unchecked")
    void registerTransferPutsCodeInExceptionText() throws Exception {
        HttpSender httpSender = mock(HttpSender.class);
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getDeviceIp()).thenReturn("127.0.0.1");
        when(deviceService.getDevicePort()).thenReturn(8080);

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(507);
        when(response.body()).thenReturn(
                "{\"code\":\"INSUFFICIENT_DISK\",\"reason\":\"UNKNOWN\",\"detail\":\"disk full\"}");
        when(httpSender.sendPinned(any(), eq("aaaa bbbb"))).thenReturn(response);

        PeerCacheService peerCache = mock(PeerCacheService.class);
        Peer peer = new Peer();
        peer.setTlsFingerprint("aaaa bbbb");
        peer.setTlsPort(9000);
        peer.setPort(9000);
        when(peerCache.findByIpAndPort("10.0.0.2", 9000)).thenReturn(peer);
        TransferHttpClient client = new TransferHttpClient(
                httpSender, new ObjectMapper(), deviceService, peerCache);

        InitiateTransferRequest request = new InitiateTransferRequest();
        request.setTargetIp("10.0.0.2");
        request.setTargetPort(9000);

        assertThatThrownBy(() -> client.registerTransfer(
                        request,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "a.bin",
                        10L))
                .isInstanceOf(FileTransferException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("INSUFFICIENT_DISK");
                    assertThat(ex.getMessage()).contains("UNKNOWN");
                    assertThat(ex.getMessage()).contains("disk full");
                });
    }

    /**
     * Register connect timeout on a LAN IP stays CONNECT_TIMEOUT.
     */
    @Test
    void registerConnectTimeoutOnLanIsConnectTimeout() throws Exception {
        HttpSender httpSender = mock(HttpSender.class);
        DeviceService deviceService = mock(DeviceService.class);
        when(deviceService.getDeviceIp()).thenReturn("10.0.0.1");
        when(deviceService.getDevicePort()).thenReturn(8080);
        when(httpSender.sendPinned(any(), eq("aaaa bbbb")))
                .thenThrow(new HttpConnectTimeoutException("connect"));

        PeerCacheService peerCache = mock(PeerCacheService.class);
        Peer peer = new Peer();
        peer.setTlsFingerprint("aaaa bbbb");
        peer.setTlsPort(9000);
        peer.setPort(9000);
        when(peerCache.findByIpAndPort("192.168.1.5", 9000)).thenReturn(peer);
        TransferHttpClient client = new TransferHttpClient(
                httpSender, new ObjectMapper(), deviceService, peerCache);

        InitiateTransferRequest request = new InitiateTransferRequest();
        request.setTargetIp("192.168.1.5");
        request.setTargetPort(9000);

        assertThatThrownBy(() -> client.registerTransfer(
                        request,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "a.bin",
                        10L))
                .isInstanceOf(FileTransferException.class)
                .satisfies(ex -> assertThat(FileTransferErrorRule.reason(ex))
                        .isEqualTo(ErrorReason.CONNECT_TIMEOUT));
    }
}
