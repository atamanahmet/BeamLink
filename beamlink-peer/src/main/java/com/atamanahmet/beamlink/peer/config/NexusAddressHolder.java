package com.atamanahmet.beamlink.peer.config;

import com.atamanahmet.beamlink.peer.domain.enums.SettingKey;
import com.atamanahmet.beamlink.peer.service.SettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
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

    private final SettingsService settingsService;
    private final AtomicReference<String> nexusUrl = new AtomicReference<>();

    @PostConstruct
    public void init() {
        settingsService.get(SettingKey.NEXUS_URL)
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

        settingsService.set(SettingKey.NEXUS_URL, url);

        log.info("Nexus URL set: {}", url);
    }

    /**
     * Clears persisted URL. Forces rediscovery on next startup.
     * Called when user explicitly wants to reconnect to a different Nexus.
     */
    public void clear() {
        nexusUrl.set(null);
        settingsService.clear(SettingKey.NEXUS_URL);
        log.info("Nexus URL cleared");
    }

    public String getHost() {
        URI uri = getNexusUri();
        return uri != null ? uri.getHost() : null;
    }

    public int getPort() {
        URI uri = getNexusUri();
        return uri != null ? uri.getPort() : -1;
    }

    public URI getNexusUri() {
        String url = nexusUrl.get();
        if (url == null) return null;
        return URI.create(url);
    }

    public boolean updateIfChanged(String newUrl) {
        String current = nexusUrl.get();
        if (newUrl == null || newUrl.equals(current)) return false;

        setNexusUrl(newUrl);
        return true;
    }
}