package com.atamanahmet.beamlink.nexus.dto;

/**
 * Body for a recovery-code password reset.
 */
public record ResetPasswordRequest(String recoveryCode, String newPassword) {
}
