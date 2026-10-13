package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(
        name = "registered_agents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ip_address", "port"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(12)
    private UUID id;
    private String name;
    private String ipAddress;
    private int port;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentState state;

    @Column(name = "public_id", unique = true)
    @JdbcTypeCode(12)
    private UUID publicId;
    private String requestedName;
    private Instant registeredAt;
    private Instant renameRequestedAt;
    private Instant approvedAt;
    private Instant lastSeenAt;
    @Column(nullable = false)
    private boolean approvalPushed;

    public boolean isOnline() {
        return this.lastSeenAt == null ? false : this.lastSeenAt.isAfter(Instant.now().minus(Duration.ofMinutes(2L)));
    }
}