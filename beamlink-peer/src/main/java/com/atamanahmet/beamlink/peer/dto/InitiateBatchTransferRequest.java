package com.atamanahmet.beamlink.peer.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InitiateBatchTransferRequest {

    private List<String> filePaths;
    private UUID dispatchId;
    private UUID targetDeviceId;
    private String targetIp;
    private int targetPort;
}