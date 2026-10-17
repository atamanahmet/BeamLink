package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Setting;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.repository.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;

    public Optional<String> get(SettingKey key) {
        return settingsRepository.findByKey(key).map(Setting::getValue);
    }

    public String get(SettingKey key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public double getDouble(SettingKey key, double defaultValue) {
        return getOptionalDouble(key).orElse(defaultValue);
    }

    /**
     * Parses a stored double setting when present.
     */
    public Optional<Double> getOptionalDouble(SettingKey key) {
        Optional<String> raw = get(key);
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Double.parseDouble(raw.get()));
        } catch (NumberFormatException e) {
            log.warn("Invalid double for key {}", key);
            return Optional.empty();
        }
    }

    public void set(SettingKey key, String value) {
        settingsRepository.save(new Setting(key, value));
    }

    public void set(SettingKey key, double value) {
        set(key, String.valueOf(value));
    }

    /**
     * Removes a setting row so the cache can treat it as unset.
     */
    public void clear(SettingKey key) {
        settingsRepository.findByKey(key).ifPresent(settingsRepository::delete);
    }
}