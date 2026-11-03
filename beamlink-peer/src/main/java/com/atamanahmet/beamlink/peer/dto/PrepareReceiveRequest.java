package com.atamanahmet.beamlink.peer.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PrepareReceiveRequest {
    private UUID transferId;
    private UUID sourceDeviceId;
    private String sourceIp;
    private Integer sourcePort;
    private String fileName;
    private Long fileSize;
}