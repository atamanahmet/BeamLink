package com.atamanahmet.beamlink.nexus.mapper;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.dto.DeviceDTO;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.dto.PeerSummaryResponse;
import com.atamanahmet.beamlink.nexus.security.NexusTlsCert;
import com.atamanahmet.beamlink.nexus.security.NexusTlsFingerprint;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PeerMapper {

    private final NexusConfig nexusConfig;
    private final NexusService nexusService;
    private final NexusTlsCert nexusTlsCert;

    public PeerDTO fromDevice(Device device) {
        return PeerDTO.builder()
                .id(device.getId())
                .deviceName(device.getName())
                .ipAddress(device.getIpAddress())
                .port(device.getPort())
                .online(device.isOnline())
                .publicId(device.getPublicId())
                .lastSeen(device.getLastSeenAt() != null
                        ? device.getLastSeenAt().toEpochMilli() : 0L)
                .tlsPort(approvedTlsPort(device))
                .tlsFingerprint(approvedTlsFingerprint(device))
                .build();
    }

    /**
     * Peer sync shape from DeviceDTO.
     */
    public PeerDTO fromDeviceDto(DeviceDTO device) {
        return PeerDTO.builder()
                .id(device.getId())
                .deviceName(device.getDeviceName())
                .ipAddress(device.getIpAddress())
                .port(device.getPort())
                .online(device.isOnline())
                .publicId(device.getPublicId())
                .lastSeen(System.currentTimeMillis())
                .tlsPort(device.getTlsPort())
                .tlsFingerprint(device.getTlsFingerprint())
                .build();
    }

    /**
     * TLS pin fields only for APPROVED devices. JSON null otherwise.
     */
    private static Integer approvedTlsPort(Device device) {
        if (device.getState() != DeviceState.APPROVED) {
            return null;
        }
        return device.getTlsPort();
    }

    /**
     * TLS pin fields only for APPROVED devices. JSON null otherwise.
     */
    private static String approvedTlsFingerprint(Device device) {
        if (device.getState() != DeviceState.APPROVED) {
            return null;
        }
        return device.getTlsFingerprint();
    }

    public PeerDTO nexusPeer() {
        return PeerDTO.builder()
                .id(nexusService.getNexusId())
                .deviceName(nexusConfig.getName())
                .ipAddress(nexusConfig.getIp())
                .port(nexusConfig.getNexusPort())
                .publicId(nexusService.getNexusId())
                .online(true)
                .lastSeen(System.currentTimeMillis())
                .tlsPort(nexusConfig.getTlsPort())
                .tlsFingerprint(NexusTlsFingerprint.of(nexusTlsCert.getLeaf()))
                .build();
    }

    /**
     * Maps device to REST peer summary. id comes from device id.
     */
    public static PeerSummaryResponse toSummary(Device device) {
        return new PeerSummaryResponse(
                device.getId(),
                device.getName(),
                device.getIpAddress(),
                device.getPort(),
                device.isOnline(),
                approvedTlsPort(device),
                approvedTlsFingerprint(device));
    }

    /**
     * Nexus itself as a REST peer summary for the UI list.
     */
    public PeerSummaryResponse nexusPeerSummary() {
        return new PeerSummaryResponse(
                nexusService.getNexusId(),
                nexusConfig.getName(),
                nexusConfig.getIp(),
                nexusConfig.getNexusPort(),
                true,
                nexusConfig.getTlsPort(),
                NexusTlsFingerprint.of(nexusTlsCert.getLeaf()));
    }
}
