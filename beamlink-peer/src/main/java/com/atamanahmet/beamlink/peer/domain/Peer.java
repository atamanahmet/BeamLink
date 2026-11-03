package com.atamanahmet.beamlink.peer.domain;

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
    private UUID deviceId;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private long lastSeen;

    @Column(nullable = false)
    private boolean online;

    private UUID publicId;

    private Integer tlsPort;

    private String tlsFingerprint;

    public String getAddress() {
        return ipAddress + ":" + port;
    }
}