package com.atamanahmet.beamlink.peer.domain;

import com.atamanahmet.beamlink.peer.domain.enums.DeviceState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.UUID;

@Entity
@Table(name = "device")
@Getter
@Setter
@NoArgsConstructor
public class Device {

    /* Only one device per instance */
    @Id
    @Column(nullable = false, updatable = false)
    private Long id = 1L;

    @Column
    @JdbcTypeCode(12)
    private UUID deviceId;

    @Column
    @JdbcTypeCode(12)
    private UUID publicId;

    @Column(nullable = false)
    private String deviceName;

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
    private DeviceState state = DeviceState.UNREGISTERED;

    public boolean isApproved() {
        return state == DeviceState.APPROVED;
    }

}