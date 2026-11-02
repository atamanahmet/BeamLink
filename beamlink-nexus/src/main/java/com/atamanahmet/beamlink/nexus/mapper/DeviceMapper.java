package com.atamanahmet.beamlink.nexus.mapper;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.dto.DeviceDTO;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class DeviceMapper {

    private static final int OFFLINE_THRESHOLD_MINUTES = 2;

    public DeviceDTO toDTO(Device device) {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));

        Instant lastSeen = device.getLastSeenAt();

        return DeviceDTO.builder()
                .id(device.getId())
                .deviceName(device.getName())
                .ipAddress(device.getIpAddress())
                .port(device.getPort())
                .online(lastSeen != null && lastSeen.isAfter(threshold))
                .publicId(device.getPublicId())
                .fingerprint(device.getFingerprint())
                .tlsPort(device.getTlsPort())
                .tlsFingerprint(device.getTlsFingerprint())
                .build();
    }
}