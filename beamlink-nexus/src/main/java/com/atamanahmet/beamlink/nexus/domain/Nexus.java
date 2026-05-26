package com.atamanahmet.beamlink.nexus.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.sql.Types;
import java.util.UUID;

/**
 * Single-row entity representing this Nexus node's identity and credentials.
 * Always accessed via id=1.
 */
@Entity
@Table(name = "nexus")
@Getter
@Setter
@NoArgsConstructor
public class Nexus {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true)
    @JdbcTypeCode(Types.VARCHAR)
    private UUID nexusId;

    @Column(nullable = false)
    private String nexusName;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private Integer port;

    @Column
    private String publicToken;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String encodedPassword;
}