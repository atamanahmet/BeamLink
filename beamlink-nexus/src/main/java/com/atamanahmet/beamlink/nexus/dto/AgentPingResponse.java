package com.atamanahmet.beamlink.nexus.dto;

/**
 * Body returned by agent ping, includes resume policy.
 */
public record AgentPingResponse(String resumePolicy) {}
