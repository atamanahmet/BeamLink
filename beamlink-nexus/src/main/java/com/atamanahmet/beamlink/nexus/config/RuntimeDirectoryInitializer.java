package com.atamanahmet.beamlink.nexus.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ensures runtime directories exist before datasource initializes.
 */
@Slf4j
@Configuration
public class RuntimeDirectoryInitializer {

    @Value("${beamlink.runtime.dir}")
    private String runtimeDir;

    @PostConstruct
    public void init() {
        String[] subdirs = {"data/database", "data/uploads", "data/temp", "logs"};
        for (String sub : subdirs) {
            Path dir = Path.of(runtimeDir, sub);
            try {
                Files.createDirectories(dir);
                log.debug("Ensured runtime dir: {}", dir);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create runtime directory: " + dir, e);
            }
        }
    }
}