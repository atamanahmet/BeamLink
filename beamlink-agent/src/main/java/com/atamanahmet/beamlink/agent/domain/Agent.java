package com.atamanahmet.beamlink.agent.domain;

import com.atamanahmet.beamlink.agent.domain.enums.AgentState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.UUID;

@Entity
@Table(name = "agent")
@Getter
@Setter
@NoArgsConstructor
public class Agent {

    /* Only one agent per instance */
    @Id
    @Column(nullable = false, updatable = false)
    private Long id = 1L;

    @Column
    @JdbcTypeCode(12)
    private UUID agentId;

    @Column
    @JdbcTypeCode(12)
    private UUID publicId;

    @Column(nullable = false)
    private String agentName;

    @Column(nullable = false)

    private String username;
    @Column(nullable = false)

    private String encodedPassword;

    @Column(nullable = false)
    private boolean credentialsConfigured = false;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private int port;

    @Column
    private String authToken;

    @Column
    private String publicToken;

    @Column(columnDefinition = "TEXT")
    private String nexusPublicKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentState state = AgentState.UNREGISTERED;

    public boolean isApproved() {
        return state == AgentState.APPROVED;
    }

}