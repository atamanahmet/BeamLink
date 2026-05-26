package com.atamanahmet.beamlink.agent.service;

import java.util.UUID;

/**
 * Carries group transfer identity and its service into the async sender.
 * Async sender never knows if this is batch or directory.
 */
public record GroupTransferContext(
        UUID groupId,
        String targetIp,
        int targetPort,
        GroupTransferService groupTransferService
) {}