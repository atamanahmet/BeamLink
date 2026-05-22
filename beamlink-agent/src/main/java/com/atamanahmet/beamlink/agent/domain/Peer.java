package com.atamanahmet.beamlink.agent.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "peers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Peer {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID agentId;

    @Column(nullable = false)
    private String agentName;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private long lastSeen;

    @Column(nullable = false)
    private boolean online;

    private UUID publicId;

    public String getAddress() {
        return ipAddress + ":" + port;
    }
}