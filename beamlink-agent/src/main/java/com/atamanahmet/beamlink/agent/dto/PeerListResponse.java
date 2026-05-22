package com.atamanahmet.beamlink.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class PeerListResponse {
    private List<PeerDTO> peers;
    private long version;
}
