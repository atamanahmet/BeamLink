package com.atamanahmet.beamlink.peer.domain;

import lombok.Data;
import java.util.List;

@Data
public class PeerCache {
    private List<Peer> peers;
    private long version;
}