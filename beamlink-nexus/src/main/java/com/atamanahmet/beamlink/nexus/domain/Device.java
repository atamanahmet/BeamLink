package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(
        name = "registered_devices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fingerprint"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(12)
    private UUID id;
    private String name;
    private String ipAddress;
    private int port;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceState state;

    @Column(name = "public_id", unique = true)
    @JdbcTypeCode(12)
    private UUID publicId;
    private String requestedName;
    private Instant registeredAt;
    private Instant renameRequestedAt;
    private Instant approvedAt;
    private Instant lastSeenAt;

    @JsonIgnore
    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "fingerprint", unique = true)
    private String fingerprint;

    @Column(name = "tls_fingerprint")
    private String tlsFingerprint;

    @Column(name = "tls_port")
    private Integer tlsPort;

    public boolean isOnline() {
        return this.lastSeenAt == null ? false : this.lastSeenAt.isAfter(Instant.now().minus(Duration.ofMinutes(2L)));
    }
}
