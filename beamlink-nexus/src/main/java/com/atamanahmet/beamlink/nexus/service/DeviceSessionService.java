package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.dto.StatusUpdatePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Session facade over DeviceService. Websocket and status paths call this.
 */
@Service
@RequiredArgsConstructor
public class DeviceSessionService {

    private final DeviceService deviceService;

    public Device findById(UUID deviceId) {
        return deviceService.findByDeviceId(deviceId);
    }

    public Device save(Device device) {
        return deviceService.saveDevice(device);
    }

    public List<Device> findApproved() {
        return deviceService.getAllApproved();
    }

    public void markOnline(UUID deviceId) {
        deviceService.markOnline(deviceId);
    }

    public void markOffline(UUID deviceId) {
        deviceService.markOffline(deviceId);
    }

    public void touchLastSeen(UUID deviceId) {
        deviceService.touchLastSeen(deviceId);
    }

    public boolean applyStatusUpdate(UUID deviceId, StatusUpdatePayload payload) {
        return deviceService.applyStatusUpdate(deviceId, payload);
    }

    /**
     * Check if device was seen recently
     */
    public boolean isOnline(Device device) {
        return deviceService.isOnline(device);
    }
}
