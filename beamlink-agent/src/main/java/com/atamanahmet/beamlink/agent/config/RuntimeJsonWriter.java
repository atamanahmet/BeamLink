package com.atamanahmet.beamlink.agent.config;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a runtime.json for electron to wire backend */
@Component
@RequiredArgsConstructor
public class RuntimeJsonWriter implements ApplicationListener<WebServerInitializedEvent> {

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        writeRuntimeJson("agent-runtime.json", port);
    }

    private void writeRuntimeJson(String filename, int port) {
        try {
            String dir = System.getProperty("beamlink.runtime.dir");
            if (dir == null || dir.isBlank()) {
                throw new IllegalStateException("beamlink.runtime.dir system property not set");
            }
            Path runtimeDir = Path.of(dir);
            Files.createDirectories(runtimeDir);
            Files.writeString(runtimeDir.resolve(filename), "{\"port\":" + port + "}");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write " + filename, e);
        }
    }
}