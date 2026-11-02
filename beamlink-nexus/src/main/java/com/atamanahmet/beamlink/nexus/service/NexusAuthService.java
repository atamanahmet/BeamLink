package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.security.AdminSessionDays;
import com.atamanahmet.beamlink.nexus.security.DeviceTokenService;
import com.atamanahmet.beamlink.nexus.security.PasswordSetupRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusAuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final NexusService nexusService;
    private final DeviceTokenService deviceTokenService;
    private final PasswordEncoder passwordEncoder;

    public boolean validateCredentials(String username, String password) {
        if (!nexusService.hasPassword()) {
            return false;
        }
        return username.equals(nexusService.getUsername())
                && passwordEncoder.matches(password, nexusService.getEncodedPassword());
    }

    /**
     * Builds an admin JWT for the chosen session length.
     */
    public String generateAdminToken(String username, String sessionDays) {
        int expirationDays = AdminSessionDays.toExpirationDays(sessionDays);
        return deviceTokenService.generateAdminToken(
                username, nexusService.getTokenVersion(), expirationDays);
    }

    /**
     * Sets the first password. Caller must enforce loopback and setupRequired.
     */
    public void setup(String username, String password) {
        if (!PasswordSetupRule.maySetup(nexusService.hasPassword())) {
            throw new IllegalStateException("Password already set");
        }
        if (username == null || username.isBlank()) {
            throw new InvalidRequestException("Username cannot be blank.");
        }
        requirePasswordLength(password);
        nexusService.setupCredentials(username.trim(), passwordEncoder.encode(password));
    }

    /**
     * Validates current password then delegates credential update to NexusService.
     */
    public void changeCredentials(String currentPassword, String newUsername, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, nexusService.getEncodedPassword())) {
            throw new InvalidRequestException("Current password is incorrect.");
        }
        if (newUsername == null || newUsername.isBlank()) {
            throw new InvalidRequestException("Username cannot be blank.");
        }
        requirePasswordLength(newPassword);
        nexusService.updateCredentials(newUsername, passwordEncoder.encode(newPassword));
    }

    /**
     * Revokes all admin sessions by bumping token_version.
     */
    public void logoutAll() {
        nexusService.bumpTokenVersion();
    }

    private void requirePasswordLength(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Password must be at least 8 characters.");
        }
    }
}
