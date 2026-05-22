package com.atamanahmet.beamlink.agent.config;

import lombok.Getter;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration class for agent settings
 */
@Configuration
@Getter
@RequiredArgsConstructor
public class AgentConfig {

    private final AgentNetworkConfig networkConfig;

    @Value("${server.port}")
    private int agentPort;

    @Value("${agent.ip-address}")
    private String configuredIp;

    @Value("${agent.ui.username}")
    private String agentUsername;

    @Value("${agent.ui.password}")
    private String agentPassword;

    @Value("${agent.transfer.expiry-hours:24}")
    private long transferExpiryHours;

    @Value("${agent.auto-resume-group-transfers:false}")
    private boolean autoResumeGroupTransfers;

    /** Returns configured IP or auto-resolves if set to auto */
    public String getIp() {
        return "auto".equalsIgnoreCase(configuredIp)
                ? networkConfig.getResolvedIp()
                : configuredIp;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

}



