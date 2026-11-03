package com.atamanahmet.beamlink.peer.dto;

import lombok.Data;

import java.util.List;

@Data
public class PeerListResponse {
    private List<PeerDTO> peers;
    private long version;
}
