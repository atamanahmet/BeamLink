package com.atamanahmet.beamlink.nexus.config;

import com.atamanahmet.beamlink.nexus.domain.Setting;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.repository.SettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT Secret resolution strategy:
 * 1. DB has priority (authoritative source after first run)
 * 2. .env is only used if DB is empty (first initialization)
 * 3. If none of them exist or env is "auto", generate a secure secret
 * After first initialization, DB becomes the ONLY source of truth.
 */
@Component
@Getter
@RequiredArgsConstructor
public class JwtSecretConfig {
    @Value("${NEXUS_JWT_SECRET:auto}")
    private String envSecret;
    private String resolvedSecret;
    private final JwtConfig jwtConfig;
    private final SettingsRepository settingsRepository;

    @PostConstruct
    public void resolve() {
        this.settingsRepository.findByKey(SettingKey.JWT_SECRET).ifPresentOrElse((setting) -> this.resolvedSecret = setting.getValue(), this::initializeFromEnvOrGenerate);
    }

    private void initializeFromEnvOrGenerate() {
        String secretToUse;
        if (this.envSecret != null && !this.envSecret.isBlank() && !"auto".equalsIgnoreCase(this.envSecret)) {
            secretToUse = this.envSecret;
        } else {
            secretToUse = this.generateSecret();
        }

        this.settingsRepository.save(new Setting(SettingKey.JWT_SECRET, secretToUse));
        this.resolvedSecret = secretToUse;
    }

    private String generateSecret() {
        byte[] bytes = new byte[64];
        (new SecureRandom()).nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}