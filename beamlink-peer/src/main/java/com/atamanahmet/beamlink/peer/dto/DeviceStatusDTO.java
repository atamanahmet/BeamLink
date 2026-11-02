package com.atamanahmet.beamlink.peer.dto;

import com.atamanahmet.beamlink.peer.domain.TransferLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Builder
public class DeviceStatusDTO {

    private UUID deviceId;
    private String deviceName;
    private String ipAddress;
    private int port;
    private boolean status;
    private int unSyncedLogs;
    private long peerVersion;
    private String publicToken;
}
