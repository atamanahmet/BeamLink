package com.atamanahmet.beamlink.nexus.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StatusUpdatePayload {
    private String ipAddress;
    private int port;
    private long peerVersion;
}