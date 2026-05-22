package com.atamanahmet.beamlink.agent.config;

import com.atamanahmet.beamlink.agent.domain.Setting;
import com.atamanahmet.beamlink.agent.domain.enums.SettingKey;
import com.atamanahmet.beamlink.agent.repository.SettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single runtime source of truth for the Nexus URL.
 *
 * Priority:
 *   1. NEXUS_URL from settings DB (persisted from previous discovery or manual entry)
 *   2. mDNS discovery (handled by NexusMdnsDiscovery after web server starts)
 *   3. Manual UI entry triggers when isResolved() is false after discovery
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NexusAddressHolder {

    private final SettingsRepository settingsRepository;
    private final AtomicReference<String> nexusUrl = new AtomicReference<>();

    @PostConstruct
    public void init() {
        settingsRepository.findByKey(SettingKey.NEXUS_URL)
                .map(Setting::getValue)
                .filter(v -> !v.isBlank())
                .ifPresent(url -> {
                    nexusUrl.set(url);
                    log.info("Nexus URL loaded from DB: {}", url);
                });
    }

    public String getNexusUrl() {
        return nexusUrl.get();
    }

    public boolean isResolved() {
        return nexusUrl.get() != null;
    }

    /**
     * Called by mDNS discovery or manual UI entry.
     * Persists to DB so subsequent launches skip discovery.
     */
    public void setNexusUrl(String url) {
        nexusUrl.set(url);

        Setting setting = settingsRepository.findByKey(SettingKey.NEXUS_URL)
                .orElse(new Setting(SettingKey.NEXUS_URL, url));
        setting.update(url);
        settingsRepository.save(setting);

        log.info("Nexus URL set: {}", url);
    }

    /**
     * Clears persisted URL. Forces rediscovery on next startup.
     * Called when user explicitly wants to reconnect to a different Nexus.
     */
    public void clear() {
        nexusUrl.set(null);
        settingsRepository.findByKey(SettingKey.NEXUS_URL)
                .ifPresent(settingsRepository::delete);
        log.info("Nexus URL cleared");
    }
}