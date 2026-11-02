package com.atamanahmet.beamlink.peer.dto;

import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TransferStatusResponse {
    private UUID transferId;
    private TransferStatus status;
    private long confirmedOffset;
    private long fileSize;
    private String fileName;
    private String failureReason;
    private UUID targetDeviceId;
    private Instant createdAt;
    private Instant lastChunkAt;
}