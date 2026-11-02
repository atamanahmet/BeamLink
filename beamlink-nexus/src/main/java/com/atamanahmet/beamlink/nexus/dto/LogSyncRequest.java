package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class LogSyncRequest {
    private UUID id;
    private UUID fromDeviceId;
    private String fromDeviceName;
    private UUID toDeviceId;
    private String toDeviceName;
    private String filename;
    private long fileSize;
    private double averageSpeedMbps;
    private Instant timestamp;
}