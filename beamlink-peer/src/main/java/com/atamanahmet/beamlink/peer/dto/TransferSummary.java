package com.atamanahmet.beamlink.peer.dto;

import com.atamanahmet.beamlink.peer.domain.enums.TransferType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TransferSummary {

    private UUID id;
    private TransferType type;
    private UUID dispatchId;
    private String name;
    private String status;
    private long totalSize;
    private long confirmedBytes;
    private int totalFiles;
    private UUID sourceDeviceId;
    private UUID targetDeviceId;
    private String targetIp;
    private Integer targetPort;
    private Instant createdAt;
    private Instant completedAt;
    private String failureReason;
    private long activeTransferMs;
    private String pausedBy;
    private boolean canResume;
    private Double speedLimitMbps;
    private Double effectiveLimitMbps;
}
