package com.atamanahmet.beamlink.nexus.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Received transfer log from all agents
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
    private UUID fromAgentId;
    private String fromAgentName;
    @JdbcTypeCode(12)
    private UUID toAgentId;
    private String toAgentName;
    private String filename;
    private long fileSize;

    @Column(nullable = false)
    private double averageSpeedMbps;

    @Column(name = "transferred_at")
    private Instant timestamp;
}