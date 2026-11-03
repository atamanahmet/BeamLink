package com.atamanahmet.beamlink.peer.mapper;

import com.atamanahmet.beamlink.peer.domain.TransferLog;
import com.atamanahmet.beamlink.peer.domain.enums.TransferSyncState;
import com.atamanahmet.beamlink.peer.dto.TransferLogDTO;

import java.time.Instant;
import java.util.UUID;

public final class TransferLogMapper {

    private TransferLogMapper() {}

    /** Creates a new entity from incoming transfer data, ready to persist. */
    public static TransferLog toEntity(TransferLogDTO dto) {
        return TransferLog.builder()
                .id(dto.getId() != null ? dto.getId() : UUID.randomUUID())
                .fromDeviceId(dto.getFromDeviceId())
                .fromDeviceName(dto.getFromDeviceName())
                .toDeviceId(dto.getToDeviceId())
                .toDeviceName(dto.getToDeviceName())
                .filename(dto.getFilename())
                .fileSize(dto.getFileSize())
                .averageSpeedMbps(dto.getAverageSpeedMbps())
                .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now())
                .syncState(TransferSyncState.PENDING)
                .build();
    }

    /** Maps entity to wire DTO for sending to Nexus. */
    public static TransferLogDTO toDTO(TransferLog entity) {
        return TransferLogDTO.builder()
                .id(entity.getId())
                .fromDeviceId(entity.getFromDeviceId())
                .fromDeviceName(entity.getFromDeviceName())
                .toDeviceId(entity.getToDeviceId())
                .toDeviceName(entity.getToDeviceName())
                .filename(entity.getFilename())
                .fileSize(entity.getFileSize())
                .averageSpeedMbps(entity.getAverageSpeedMbps())
                .timestamp(entity.getTimestamp())
                .build();
    }
}