package com.atamanahmet.beamlink.nexus.dto;

/**
 * Body for regenerating the recovery code.
 */
public record RegenerateRecoveryRequest(String currentPassword) {
}
