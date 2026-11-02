package com.atamanahmet.beamlink.peer.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InitiateSendRequest {
    private List<String> paths;
    private UUID targetDeviceId;
    private String targetIp;
    private int targetPort;
}