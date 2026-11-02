package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.time.Instant;

@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(Types.INTEGER)
    private Long id;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, columnDefinition = "TEXT")
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, columnDefinition = "TEXT")
    private AuditOutcome outcome;

    @Column(name = "source_address", columnDefinition = "TEXT")
    private String sourceAddress;

    @Column(name = "target_device_id", columnDefinition = "TEXT")
    private String targetDeviceId;

    @Column(columnDefinition = "TEXT")
    private String detail;
}
