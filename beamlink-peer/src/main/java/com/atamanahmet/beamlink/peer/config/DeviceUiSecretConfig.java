package com.atamanahmet.beamlink.peer.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Fails startup when the UI secret env is missing.
 */
@Configuration
public class DeviceUiSecretConfig {

    @Value("${PEER_UI_SECRET:}")
    private String uiSecret;

    @PostConstruct
    public void requireSecret() {
        if (uiSecret == null || uiSecret.isBlank()) {
            throw new IllegalStateException("PEER_UI_SECRET is required");
        }
    }
}
