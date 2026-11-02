package com.atamanahmet.beamlink.nexus.dto;

public record DeviceStats(
        long total,
        long online,
        long offline,
        long pending,
        long pendingRename
) {}