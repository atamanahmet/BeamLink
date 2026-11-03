package com.atamanahmet.beamlink.peer.event;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.domain.Device;
import com.atamanahmet.beamlink.peer.security.DeviceKey;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import com.atamanahmet.beamlink.peer.service.DeviceService;
import com.atamanahmet.beamlink.peer.service.PeerCacheService;
import com.atamanahmet.beamlink.peer.service.SseEmitters;
import com.atamanahmet.beamlink.peer.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeviceIdentityEventListener {

    private final SseEmitters sseEmitters;
    private final DeviceService deviceService;
    private final DeviceConfig deviceConfig;
    private final NexusAddressHolder nexusAddressHolder;
    private final StorageService storageService;
    private final PeerCacheService peerCacheService;
    private final DeviceKey deviceKey;
    private final NexusTlsPin nexusTlsPin;

    @EventListener
    public void onApproved(DeviceApprovedEvent event) {
        broadcastIdentity();
        broadcastStatus();
    }

    @EventListener
    public void onIdentityChanged(DeviceIdentityChangedEvent event) {
        broadcastIdentity();
        broadcastStatus();
    }

    private void broadcastIdentity() {
        sseEmitters.broadcast("identity_updated", identityUpdatedPayload(deviceService.getDevice()));
    }

    /**
     * Identity SSE body. Public facts only.
     */
    public static Map<String, String> identityUpdatedPayload(Device device) {
        return Map.of(
                "deviceId", device.getDeviceId() != null ? device.getDeviceId().toString() : "",
                "deviceName", device.getDeviceName() != null ? device.getDeviceName() : "",
                "state", device.getState() != null ? device.getState().name() : "");
    }

    private void broadcastStatus() {
        String hint = peerCacheService.getLastNexusAuthHint();
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", deviceService.getDeviceId() != null ? deviceService.getDeviceId().toString() : "");
        payload.put("name", deviceService.getDeviceName() != null ? deviceService.getDeviceName() : "");
        payload.put("port", deviceConfig.getDevicePort());
        payload.put("uploadDirectory", storageService.getUploadsDir().toString());
        payload.put("fileCount", storageService.getFileCount());
        payload.put("status", "online");
        payload.put("nexusUrl", nexusAddressHolder.getNexusUrl() != null ? nexusAddressHolder.getNexusUrl() : "");
        payload.put("state", deviceService.getState() != null ? deviceService.getState().name() : "");
        payload.put("pinSet", nexusTlsPin.hasPin());
        payload.put("hint", hint != null ? hint : "");
        payload.put("fingerprint", deviceKey.fingerprint());
        sseEmitters.broadcast("device_status_updated", payload);
    }
}