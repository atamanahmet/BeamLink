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
        return get(key).map(v -> {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException e) {
                log.warn("Invalid double for key {}, using default {}", key, defaultValue);
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public void set(SettingKey key, String value) {
        settingsRepository.save(new Setting(key, value));
    }

    public void set(SettingKey key, double value) {
        set(key, String.valueOf(value));
    }
}