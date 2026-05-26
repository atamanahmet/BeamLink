package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusAuthService {

    private final NexusService nexusService;
    private final AgentTokenService agentTokenService;
    private final PasswordEncoder passwordEncoder;

    public boolean validateCredentials(String username, String password) {
        return username.equals(nexusService.getUsername())
                && passwordEncoder.matches(password, nexusService.getEncodedPassword());
    }

    public String generateAdminToken(String username) {
        return agentTokenService.generateAdminToken(username);
    }

    /**
     * Validates current password then delegates credential update to NexusService.
     */
    public void changeCredentials(String currentPassword, String newUsername, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, nexusService.getEncodedPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (newUsername == null || newUsername.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }
        nexusService.updateCredentials(newUsername, passwordEncoder.encode(newPassword));
    }
}