package com.atamanahmet.beamlink.agent.config;

import com.atamanahmet.beamlink.agent.domain.Setting;
import com.atamanahmet.beamlink.agent.domain.enums.SettingKey;
import com.atamanahmet.beamlink.agent.repository.SettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Resolves the JWT secret at startup.
 * If "auto" is configured, generates a cryptographically secure secret
 * and persists it to the settings table for subsequent startups.
 * Persisted secret always takes precedence over .env value.
 */
@Component
@Getter
@RequiredArgsConstructor
public class JwtSecretConfig {

    private String resolvedSecret;

    private final SettingsRepository settingsRepository;

    @PostConstruct
    public void resolve() {
        settingsRepository.findByKey(SettingKey.JWT_SECRET)
                .ifPresentOrElse(
                        setting -> this.resolvedSecret = setting.getValue(),
                        this::generateAndPersist
                );
    }

    private void generateAndPersist() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        this.resolvedSecret = Base64.getEncoder().encodeToString(bytes);
        settingsRepository.save(new Setting(SettingKey.JWT_SECRET, this.resolvedSecret));
    }
}