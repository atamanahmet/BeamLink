package com.atamanahmet.beamlink.peer.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferLogDTO {
    private UUID id;
    private UUID fromDeviceId;
    private String fromDeviceName;
    private UUID toDeviceId;
    private String toDeviceName;
    private String filename;
    private long fileSize;
    private Instant timestamp;
    private double averageSpeedMbps;
}