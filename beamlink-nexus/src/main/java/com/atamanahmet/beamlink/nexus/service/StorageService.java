package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.StorageConfig;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.exception.InsufficientDiskSpaceException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    static final String PARTIAL_DIR_NAME = ".beamlink-partial";

    private final StorageConfig storageConfig;
    private final SettingsService settingsService;

    @Getter
    private Path uploadsDir;

    @PostConstruct
    public void init() {
        this.uploadsDir = resolveUploadsDir();
        createDirectory(this.uploadsDir);
        ensurePartialDir();
    }

    public void updateUploadsDir(Path newPath) {
        createDirectory(newPath);
        settingsService.set(SettingKey.STORAGE_UPLOAD_DIR, newPath.toString());
        this.uploadsDir = newPath;
        ensurePartialDir();
        log.info("Uploads directory updated to: {}", newPath);
    }

    public Path getLogsPath() {
        return storageConfig.getDefaultLogsDir().resolve("nexus.log");
    }

    private Path resolveUploadsDir() {
        return settingsService.get(SettingKey.STORAGE_UPLOAD_DIR)
                .map(Path::of)
                .orElseGet(() -> {
                    Path fallback = storageConfig.getDefaultUploadsDir();
                    log.warn("No upload directory configured in DB, using default: {}", fallback);
                    return fallback;
                });
    }

    /**
     * Hidden folder inside the upload directory for PARTIAL files.
     */
    public Path getPartialDir() {
        return uploadsDir.resolve(PARTIAL_DIR_NAME);
    }

    /**
     * Creates the hidden partial folder and marks it hidden on Windows.
     */
    public void ensurePartialDir() {
        Path dir = getPartialDir();
        createDirectory(dir);
        hideWindows(dir);
    }

    /**
     * Sets the Windows hidden attribute. No-op on other volumes.
     */
    private void hideWindows(Path dir) {
        try {
            Files.setAttribute(dir, "dos:hidden", true);
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("Could not hide partial dir {}: {}", dir, e.getMessage());
        }
    }

    public Path resolvePartialPath(String fileName) {
        ensurePartialDir();
        return getPartialDir().resolve(fileName + ".part");
    }

    /**
     * Stored receive path when set, else the current hidden-folder path.
     */
    public Path storedPartialPath(FileTransfer transfer) {
        String stored = transfer.getFilePath();
        if (stored != null && !stored.isBlank()) {
            return Path.of(stored);
        }
        return resolvePartialPath(transfer.getFileName());
    }

    public void allocatePartialFile(Path path, long size) {
        try {
            Files.createDirectories(path.getParent());
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
                raf.setLength(size);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate partial file: " + path, e);
        }
    }

    /**
     * Atomic same-volume rename. Never copies.
     */
    public void moveToFinalLocation(FileTransfer transfer) {
        Path partialFile = storedPartialPath(transfer);

        Path finalPath;
        if (transfer.getRelativePath() != null && transfer.getDirectoryName() != null) {
            finalPath = uploadsDir
                    .resolve(transfer.getDirectoryName())
                    .resolve(transfer.getRelativePath());
        } else {
            finalPath = uploadsDir.resolve(transfer.getFileName());
        }

        try {
            Files.createDirectories(finalPath.getParent());
            if (Files.exists(finalPath)) {
                Files.delete(finalPath);
            }
            Files.move(partialFile, finalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            throw new IllegalStateException(
                    "Atomic move is not supported on this volume. Keep the upload directory on the same volume as the partial file.",
                    e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to move completed file to upload directory", e);
        }
    }

    public void deletePartialFile(String fileName) {
        try {
            Files.deleteIfExists(resolvePartialPath(fileName));
        } catch (IOException e) {
            log.warn("Could not delete partial file for: {}", fileName);
        }
    }

    /**
     * Deletes the stored partial path for this row.
     */
    public void deletePartialFile(FileTransfer transfer) {
        try {
            Files.deleteIfExists(storedPartialPath(transfer));
        } catch (IOException e) {
            log.warn("Could not delete partial file for: {}", transfer.getFileName());
        }
    }

    /**
     * Usable bytes on the upload directory volume.
     */
    public long usableBytes() {
        try {
            FileStore store = Files.getFileStore(uploadsDir);
            return store.getUsableSpace();
        } catch (IOException e) {
            log.warn("Unable to read usable space: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Throws when free space on the receive volume is below requiredBytes.
     */
    public void checkDiskSpace(long requiredBytes) {
        long usable = usableBytes();
        if (usable < requiredBytes) {
            throw new InsufficientDiskSpaceException(
                    String.format("Insufficient disk space. Required: %d MB, Available: %d MB",
                            requiredBytes / (1024 * 1024),
                            usable / (1024 * 1024))
            );
        }
    }

    /**
     * True when the receive volume has enough free bytes.
     */
    public boolean checkDiskSpaceAvailable(long requiredBytes) {
        try {
            checkDiskSpace(requiredBytes);
            return true;
        } catch (InsufficientDiskSpaceException e) {
            log.warn("Disk space check failed: {}", e.getMessage());
            return false;
        }
    }

    public void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create directory: " + path, e);
        }
    }
}
