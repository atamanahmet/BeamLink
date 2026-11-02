package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Instant occurredAt,
        AuditEventType eventType,
        AuditOutcome outcome,
        String sourceAddress,
        String targetDeviceId,
        String detail
) {}
