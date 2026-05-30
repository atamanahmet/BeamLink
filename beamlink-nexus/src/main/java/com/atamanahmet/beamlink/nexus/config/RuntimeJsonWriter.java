package com.atamanahmet.beamlink.nexus.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes nexus-runtime.json for Electron to wire backend port and IP.
 */
@Component
@RequiredArgsConstructor
public class RuntimeJsonWriter implements ApplicationListener<WebServerInitializedEvent> {

    @Value("${beamlink.runtime.dir:./data}")
    private String runtimeDir;

    private final NexusNetworkConfig networkConfig;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String ip = networkConfig.getResolvedIp();
        writeRuntimeJson(port, ip);
    }

    private void writeRuntimeJson(int port, String ip) {
        Path dir = Path.of(runtimeDir);
        try {
            Files.writeString(dir.resolve("nexus-runtime.json"),
                    "{\"port\":" + port + ",\"ip\":\"" + ip + "\"}");
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to write nexus-runtime.json to: " + dir, e);
        }
    }
}