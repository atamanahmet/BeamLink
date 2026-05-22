package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.StorageConfig;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.Setting;
import com.atamanahmet.beamlink.agent.domain.enums.SettingKey;
import com.atamanahmet.beamlink.agent.exception.InsufficientDiskSpaceException;
import com.atamanahmet.beamlink.agent.repository.SettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageConfig storageConfig;
    private final SettingsRepository settingsRepository;
    private static final int BUFFER_SIZE = 8192; // 8mb
    private static final int WRITE_BUFFER_SIZE = 65536;
    private static final long DISK_BUFFER_BYTES = 100L * 1024 * 1024;


    @Getter
    private Path uploadsDir;

    @Getter
    private Path tempDir;

    @PostConstruct
    public void init() {
        this.uploadsDir = resolveUploadsDir();
        this.tempDir = resolveTempDir();

        createDirectory(this.uploadsDir);
        createDirectory(this.tempDir);
    }

    public Path getLogsPath() {
        return storageConfig.getDefaultLogsDir().resolve("agent.log");
    }

    public void updateUploadsDir(Path newPath) {
        createDirectory(newPath);
        settingsRepository.save(new Setting(SettingKey.STORAGE_UPLOAD_DIR, newPath.toString().replace("\\", "/")));
        this.uploadsDir = newPath;
        log.info("Uploads directory updated to: {}", newPath);
    }


    private Path resolveUploadsDir() {
        return settingsRepository.findByKey(SettingKey.STORAGE_UPLOAD_DIR)
                .map(s -> Path.of(s.getValue().replace("\\", "/")))
                .orElseGet(() -> {
                    Path fallback = storageConfig.getDefaultUploadsDir();
                    log.warn("No upload directory configured in DB, using default: {}", fallback);
                    return fallback;
                });
    }

    /**
     * Resolves temp directory from DB if configured, falls back to default.
     * DB-based configuration not yet exposed via admin API, will fall back
     * to StorageConfig default until a STORAGE_TEMP_DIR setting is persisted.
     */
    private Path resolveTempDir() {
        return settingsRepository.findByKey(SettingKey.STORAGE_TEMP_DIR)
                .map(s -> Path.of(s.getValue().replace("\\", "/")))
                .orElseGet(() -> {
                    Path fallback = storageConfig.getDefaultTempDir();
                    log.warn("No temp directory configured in DB, using default: {}", fallback);
                    return fallback;
                });
    }

    public Path resolvePartialPath(String fileName) {
        return tempDir.resolve(fileName + ".part");
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

    /** Writes chunk at offset, returns bytes written. */
    public long writeChunk(Path path, long offset, InputStream stream) {
        long bytesWritten = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.seek(offset);
            int bytesRead;
            while ((bytesRead = stream.read(buffer)) != -1) {
                raf.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write chunk at offset " + offset, e);
        }

        return bytesWritten;
    }

    public void moveToFinalLocation(FileTransfer transfer) {
        Path partialFile = resolvePartialPath(transfer.getFileName());

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
            Files.move(partialFile, finalPath, StandardCopyOption.REPLACE_EXISTING);
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
     * Writes a full stream to a temp file, then atomically moves it to uploads.
     * Returns bytes written.
     */
    public long receiveFileStream(InputStream inputStream, String filename, long fileSize) {
        checkDiskSpace(fileSize);

        Path tmpPath   = tempDir.resolve(filename + ".tmp");
        Path finalPath = uploadsDir.resolve(filename);

        long bytesWritten = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (BufferedOutputStream out = new BufferedOutputStream(
                new FileOutputStream(tmpPath.toFile()), WRITE_BUFFER_SIZE)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }
            out.flush();
        } catch (IOException e) {
            deleteQuietly(tmpPath);
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("no space left")) {
                throw new InsufficientDiskSpaceException("No space left on device");
            }
            throw new IllegalStateException("Failed to write file to disk", e);
        }

        try {
            Files.move(tmpPath, finalPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tmpPath);
            throw new IllegalStateException("Failed to finalize file after transfer", e);
        }

        return bytesWritten;
    }

    public int getFileCount() {
        File dir = new File(getUploadsDir().toString());
        File[] files = dir.listFiles();
        return files != null ? files.length : 0;
    }

    public void checkDiskSpace(long requiredBytes) {
        try {
            FileStore store = Files.getFileStore(uploadsDir);
            long usable = store.getUsableSpace();
            long required = requiredBytes + DISK_BUFFER_BYTES;
            if (usable < required) {
                throw new InsufficientDiskSpaceException(
                        String.format("Insufficient disk space. Required: %d MB, Available: %d MB",
                                required / (1024 * 1024),
                                usable / (1024 * 1024))
                );
            }
        } catch (InsufficientDiskSpaceException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Unable to check disk space: {}", e.getMessage());
        }
    }

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

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", path);
        }
    }

}