package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.service.DeviceSessionService;
import com.atamanahmet.beamlink.nexus.service.FileTransferService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import com.atamanahmet.beamlink.nexus.service.PeerListService;
import com.atamanahmet.beamlink.nexus.service.TransferLogService;
import com.atamanahmet.beamlink.nexus.service.TransferSenderService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceWebSocketHandlerReplaceTest {

    @Test
    void secondPutClosesFirst() throws Exception {
        DeviceSessionService deviceSessions = mock(DeviceSessionService.class);
        DeviceWebSocketHandler handler = handler(deviceSessions);
        UUID deviceId = UUID.randomUUID();
        WebSocketSession first = session(deviceId, true);
        WebSocketSession second = session(deviceId, true);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        verify(first).close(CloseStatus.POLICY_VIOLATION.withReason("replaced"));
        verify(second, never()).close(any());
        assertThat(handler.getSession(deviceId)).isSameAs(second);
    }

    @Test
    void oldCloseDoesNotRemoveNew() {
        DeviceSessionService deviceSessions = mock(DeviceSessionService.class);
        DeviceWebSocketHandler handler = handler(deviceSessions);
        UUID deviceId = UUID.randomUUID();
        WebSocketSession first = session(deviceId, true);
        WebSocketSession second = session(deviceId, true);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);

        verify(deviceSessions, never()).markOffline(deviceId);
        assertThat(handler.getSession(deviceId)).isSameAs(second);
        assertThat(handler.isConnected(deviceId)).isTrue();
    }

    @Test
    void sameSessionTwiceDoesNotCloseItself() throws Exception {
        DeviceWebSocketHandler handler = handler(mock(DeviceSessionService.class));
        UUID deviceId = UUID.randomUUID();
        WebSocketSession session = session(deviceId, true);

        handler.afterConnectionEstablished(session);
        handler.afterConnectionEstablished(session);

        verify(session, never()).close(any());
        assertThat(handler.getSession(deviceId)).isSameAs(session);
    }

    @Test
    void zombieCleanupRemovesCurrentAndMarksOffline() {
        DeviceSessionService deviceSessions = mock(DeviceSessionService.class);
        DeviceWebSocketHandler handler = handler(deviceSessions);
        UUID deviceId = UUID.randomUUID();
        WebSocketSession current = session(deviceId, true);

        handler.afterConnectionEstablished(current);
        handler.handleZombieCleanup(deviceId, current);

        verify(deviceSessions, times(1)).markOffline(deviceId);
        assertThat(handler.getSession(deviceId)).isNull();
    }

    @Test
    void zombieCleanupKeepsReplacementAndSkipsOffline() {
        DeviceSessionService deviceSessions = mock(DeviceSessionService.class);
        DeviceWebSocketHandler handler = handler(deviceSessions);
        UUID deviceId = UUID.randomUUID();
        WebSocketSession first = session(deviceId, true);
        WebSocketSession second = session(deviceId, true);

        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        handler.handleZombieCleanup(deviceId, first);

        verify(deviceSessions, never()).markOffline(deviceId);
        assertThat(handler.getSession(deviceId)).isSameAs(second);
    }

    /**
     * Builds a handler with stubs that let handshake finish.
     */
    private static DeviceWebSocketHandler handler(DeviceSessionService deviceSessions) {
        FileTransferService transfers = mock(FileTransferService.class);
        when(transfers.findByTargetDeviceIdAndStatus(any(), any(TransferStatus.class)))
                .thenReturn(List.of());
        PeerMapper peers = mock(PeerMapper.class);
        when(peers.nexusPeer()).thenReturn(PeerDTO.builder().build());
        when(deviceSessions.findApproved()).thenReturn(List.of());
        PeerListService peerList = mock(PeerListService.class);
        when(peerList.getCurrentVersion()).thenReturn(0L);
        return new DeviceWebSocketHandler(
                mock(NexusConfig.class),
                deviceSessions,
                peerList,
                mock(TransferLogService.class),
                mock(TransferSenderService.class),
                transfers,
                new ObjectMapper(),
                mock(NexusService.class),
                peers);
    }

    /**
     * Open mock session whose attributes carry this device id.
     */
    private static WebSocketSession session(UUID deviceId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of("deviceId", deviceId));
        when(session.isOpen()).thenReturn(open);
        return session;
    }
}
