package com.atamanahmet.beamlink.peer.config;

import lombok.Getter;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Configuration class for device settings
 */
@Configuration
@Getter
@RequiredArgsConstructor
public class DeviceConfig {

    private final DeviceNetworkConfig networkConfig;

    @Value("${server.port}")
    private int devicePort;

    @Value("${peer.tls.port}")
    private int tlsPort;

    @Value("${peer.ip-address}")
    private String configuredIp;

    @Value("${peer.transfer.expiry-hours:24}")
    private long transferExpiryHours;

    @Value("${peer.auto-resume-group-transfers:false}")
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



