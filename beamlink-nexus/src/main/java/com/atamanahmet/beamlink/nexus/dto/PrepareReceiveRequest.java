package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PrepareReceiveRequest {
    private UUID transferId;
    private UUID sourceAgentId;
    private String fileName;
    private long fileSize;
}