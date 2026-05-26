package com.atamanahmet.beamlink.nexus.dto;

public record ChangeCredentialsRequest(
        String currentPassword,
        String newUsername,
        String newPassword
) {}