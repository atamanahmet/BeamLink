package com.atamanahmet.beamlink.nexus.config;

import com.atamanahmet.beamlink.nexus.security.DynamicCorsRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Nexus configuration
 */
@Getter
@Configuration
@RequiredArgsConstructor
public class NexusConfig {

    private final NexusNetworkConfig networkConfig;
    private final DynamicCorsRegistry corsRegistry;

    @Value("${nexus.transfer.expiry-hours}")
    private long transferExpiryHours;
    @Value("${nexus.ip:auto}")
    private String configuredIp;
    @Value("${server.port}")
    private int nexusPort;
    @Value("${nexus.name}")
    private String name;
    @Value("${nexus.admin.username}")
    private String adminUsername;
    @Value("${nexus.admin.password}")
    private String adminPassword;

    @PostConstruct
    public void registerSelfOrigin() {
        String origin = "http://" + getIp() + ":" + nexusPort;
        corsRegistry.register(origin);
    }

    public String getIp() {
        return "auto".equalsIgnoreCase(this.configuredIp) ? this.networkConfig.getResolvedIp() : this.configuredIp;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    }
}
