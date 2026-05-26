package com.atamanahmet.beamlink.agent.dto;

public record ChangeCredentialsRequest(
        String currentPassword,
        String newUsername,
        String newPassword
) {}