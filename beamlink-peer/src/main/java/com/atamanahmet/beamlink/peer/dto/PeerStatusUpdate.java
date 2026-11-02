package com.atamanahmet.beamlink.peer.dto;

import lombok.Data;

@Data
public class PeerStatusUpdate {
    private String deviceId;
    private boolean online;
}
