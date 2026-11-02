package com.atamanahmet.beamlink.nexus.event;

import com.atamanahmet.beamlink.nexus.dto.DeviceDTO;

public record DeviceApprovedEvent(DeviceDTO deviceDTO) {}