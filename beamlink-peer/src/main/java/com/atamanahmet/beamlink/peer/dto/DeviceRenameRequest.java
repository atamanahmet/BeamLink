package com.atamanahmet.beamlink.peer.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRenameRequest {
    private String name;
}
