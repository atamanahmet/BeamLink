package com.atamanahmet.beamlink.peer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
public class StorageConfig {

    @Value("${beamlink.runtime.dir}")
    private String runtimeDir;

    public Path getDefaultUploadsDir() {

        return Path.of(runtimeDir, "data", "uploads");
    }

    public Path getDefaultLogsDir() {

        return Path.of(runtimeDir, "logs");
    }
}