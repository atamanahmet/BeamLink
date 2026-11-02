package com.atamanahmet.beamlink.nexus.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatusResponse {
    private String status;
    private boolean peerOutdated;
    private List<PeerDTO> peers;
}