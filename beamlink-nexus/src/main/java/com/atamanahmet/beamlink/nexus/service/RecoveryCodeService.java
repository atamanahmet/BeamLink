package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.security.RecoveryCodeFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecoveryCodeService {

    static final String ISSUED_LOG = "Admin recovery code issued";
    static final String USED_LOG = "Admin recovery code was used";

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final NexusService nexusService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /**
     * Stores a new hash and returns the code once.
     */
    @Transactional
    public String issueNew() {
        String display = rotate();
        log.info(ISSUED_LOG);
        return display;
    }

    /**
     * Resets the password with the code, revokes sessions, and issues a new code.
     */
    @Transactional
    public Optional<String> resetPassword(String code, String newPassword) {
        String normalized = RecoveryCodeFormat.normalize(code);
        String stored = nexusService.getEncodedRecoveryCode();
        if (stored == null || stored.isBlank() || normalized.isEmpty()
                || !passwordEncoder.matches(normalized, stored)) {
            return Optional.empty();
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new InvalidRequestException("Password must be at least 8 characters.");
        }
        nexusService.updateCredentials(
                nexusService.getUsername(), passwordEncoder.encode(newPassword));
        String next = rotate();
        log.info(USED_LOG);
        return Optional.of(next);
    }

    /**
     * Writes a new hash and returns the display code.
     */
    private String rotate() {
        String hex = RecoveryCodeFormat.newHex(random);
        nexusService.setEncodedRecoveryCode(passwordEncoder.encode(hex));
        return RecoveryCodeFormat.formatDisplay(hex);
    }

    /**
     * Issues a new code when the current password matches.
     */
    @Transactional
    public String regenerate(String currentPassword) {
        if (!nexusService.hasPassword()
                || !passwordEncoder.matches(currentPassword, nexusService.getEncodedPassword())) {
            throw new InvalidRequestException("Current password is incorrect.");
        }
        return issueNew();
    }
}
