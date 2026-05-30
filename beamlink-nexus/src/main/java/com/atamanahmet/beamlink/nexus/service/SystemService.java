package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.exception.SystemOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
public class SystemService {

    /**
     * Opens a file or directory with the OS default application.
     */
    public void openWithOs(Path target) {
        String absolutePath = target.toAbsolutePath().toString();
        String os = System.getProperty("os.name").toLowerCase();

        ProcessBuilder pb;
        if (os.contains("win")) {
            pb = new ProcessBuilder("explorer.exe", absolutePath);
        } else if (os.contains("mac")) {
            pb = new ProcessBuilder("open", absolutePath);
        } else {
            pb = new ProcessBuilder("xdg-open", absolutePath);
        }

        try {
            pb.start();
            log.info("Opened path with OS default app: {}", absolutePath);
        } catch (IOException e) {
            log.error("Failed to open path with OS default app: {}", absolutePath, e);
            throw new SystemOperationException("Failed to open path: " + absolutePath, e);
        }
    }
}