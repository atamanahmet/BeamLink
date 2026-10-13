package com.atamanahmet.beamlink.nexus.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class PeerListResponse {
    private List<PeerDTO> peers;
    private long version;
}
