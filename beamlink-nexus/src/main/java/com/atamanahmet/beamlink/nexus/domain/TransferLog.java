package com.atamanahmet.beamlink.nexus.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Received transfer log from all devices
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transfer_logs")
@Builder
public class TransferLog {
    @Id
    @JdbcTypeCode(12)
    private UUID id;
    @JdbcTypeCode(12)
    private UUID fromDeviceId;
    private String fromDeviceName;
    @JdbcTypeCode(12)
    private UUID toDeviceId;
    private String toDeviceName;
    private String filename;
    private long fileSize;

    @Column(nullable = false)
    private double averageSpeedMbps;

    @Column(name = "transferred_at")
    private Instant timestamp;
}