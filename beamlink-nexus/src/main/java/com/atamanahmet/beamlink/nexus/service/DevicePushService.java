package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.dto.DeviceDTO;
import com.atamanahmet.beamlink.nexus.dto.DeviceRenameResponse;
import com.atamanahmet.beamlink.nexus.event.DeviceApprovedEvent;
import com.atamanahmet.beamlink.nexus.event.DeviceRenamedEvent;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.websocket.DeviceWebSocketHandler;
import com.atamanahmet.beamlink.nexus.websocket.WsEnvelope;
import com.atamanahmet.beamlink.nexus.websocket.enums.WsMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevicePushService {

    private final DeviceWebSocketHandler webSocketHandler;
    private final PeerMapper peerMapper;

    /**
     * Pushes a rename to the device over WS. No HTTP fallback.
     */
    public void pushRename(DeviceDTO deviceDTO) {
        if (!webSocketHandler.isConnected(deviceDTO.getId())) {
            log.warn("Rename skipped, device {} not connected on WS", deviceDTO.getId());
            return;
        }
        pushRenameViaWs(deviceDTO);
    }

    private void pushRenameViaWs(DeviceDTO deviceDTO) {
        try {
            webSocketHandler.sendMessage(
                    deviceDTO.getId(),
                    WsEnvelope.of(WsMessageType.RENAME_PUSH, DeviceRenameResponse.builder()
                            .deviceName(deviceDTO.getDeviceName())
                            .build())
            );
            log.info("Rename pushed via WS to device {}: {}", deviceDTO.getId(), deviceDTO.getDeviceName());
        } catch (Exception e) {
            log.warn("WS rename push failed for device {}: {}", deviceDTO.getId(), e.getMessage());
        }
    }

    /**
     * Tells other devices this device joined. Tokens are polled, not pushed.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeviceApproved(DeviceApprovedEvent event) {
        webSocketHandler.broadcastDeviceJoined(peerMapper.fromDeviceDto(event.deviceDTO()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeviceRenamed(DeviceRenamedEvent event) {
        pushRename(event.deviceDTO());
    }
}
