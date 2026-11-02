package com.atamanahmet.beamlink.nexus.dto;

/**
 * Body returned by device ping, includes resume policy.
 */
public record DevicePingResponse(String resumePolicy) {}
