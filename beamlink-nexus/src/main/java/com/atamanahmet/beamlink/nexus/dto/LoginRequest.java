package com.atamanahmet.beamlink.nexus.dto;

/**
 * Admin login body with optional session length.
 */
public record LoginRequest(String username, String password, String sessionDays) {
}
