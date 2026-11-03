package com.atamanahmet.beamlink.peer.domain;

import com.atamanahmet.beamlink.peer.domain.enums.TransferSyncState;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;import java.util.UUID;

/**
 * Represents a file transfer event
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "transfer_logs"
)
@Builder
public class TransferLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    private UUID fromDeviceId;
    private String fromDeviceName;
    private UUID toDeviceId;
    private String toDeviceName;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private double averageSpeedMbps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferSyncState syncState;

}