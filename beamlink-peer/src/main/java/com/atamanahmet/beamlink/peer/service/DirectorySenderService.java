package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.peer.dto.InitiateDirectoryTransferRequest;
import com.atamanahmet.beamlink.peer.dto.InitiateDirectoryTransferResponse;
import com.atamanahmet.beamlink.peer.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.peer.exception.FileTransferException;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.util.PathNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorySenderService {

    private final DirectoryTransferService directoryTransferService;
    private final DeviceConfig deviceConfig;
    private final GroupTransferAsyncSender groupTransferAsyncSender;
    private final DeviceService deviceService;
    private final TransferHttpClient transferHttpClient;

    public InitiateDirectoryTransferResponse initiate(InitiateDirectoryTransferRequest request) {
        String cleanedPath = PathNormalizer.normalize(request.getSourcePath());
        Path sourceDir = Paths.get(cleanedPath);

        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new FileTransferException("Directory not found: " + cleanedPath, null);
        }
        if (SkipSourceEntryRule.shouldSkip(sourceDir)) {
            return new InitiateDirectoryTransferResponse(null, 1);
        }

        WalkResult walk = walkDirectory(sourceDir);
        if (walk.files().isEmpty()) {
            return new InitiateDirectoryTransferResponse(null, walk.skipped());
        }
        UUID directoryTransferId = UUID.randomUUID();
        UUID sourceDeviceId = deviceService.getDeviceId();
        String directoryName = sourceDir.getFileName().toString();

        directoryTransferService.create(
                directoryTransferId,
                sourceDeviceId,
                request.getTargetDeviceId(),
                request.getTargetIp(),
                request.getTargetPort(),
                directoryName,
                cleanedPath,
                walk.files().size(),
                walk.totalSize(),
                walk.emptyDirectories(),
                request.getDispatchId()
        );

        List<FileTransfer> fileTransfers = new ArrayList<>();
        List<ReceiveDirectoryRequest.FileEntry> fileEntries = new ArrayList<>();

        for (WalkResult.FileEntry entry : walk.files()) {
            UUID transferId = UUID.randomUUID();
            String fileName = entry.absolutePath().getFileName().toString();
            String relativePath = sourceDir.relativize(entry.absolutePath()).toString();

            FileTransfer ft = FileTransfer.initiate(
                    transferId,
                    sourceDeviceId,
                    request.getTargetDeviceId(),
                    fileName,
                    entry.absolutePath().toString(),
                    entry.fileSize()
            );
            ft.setDirectoryTransferId(directoryTransferId);
            ft.setRelativePath(relativePath);
            ft.setTargetIp(request.getTargetIp());
            ft.setTargetPort(request.getTargetPort());
            ft.setExpiresAt(Instant.now().plusSeconds(deviceConfig.getTransferExpiryHours() * 3600L));
            fileTransfers.add(ft);

            ReceiveDirectoryRequest.FileEntry fe = new ReceiveDirectoryRequest.FileEntry();
            fe.setTransferId(transferId);
            fe.setFileName(fileName);
            fe.setRelativePath(relativePath);
            fe.setFileSize(entry.fileSize());
            fileEntries.add(fe);
        }

        directoryTransferService.saveChildren(fileTransfers);
        registerOnTarget(request, directoryTransferId, sourceDeviceId, directoryName, walk, fileEntries);
        directoryTransferService.markActive(directoryTransferId);

        GroupTransferContext ctx = new GroupTransferContext(
                directoryTransferId,
                request.getTargetIp(),
                request.getTargetPort(),
                directoryTransferService
        );
        groupTransferAsyncSender.sendAsync(ctx);

        log.info("Directory transfer initiated: {} → {} ({})",
                directoryName, request.getTargetDeviceId(), directoryTransferId);

        return new InitiateDirectoryTransferResponse(directoryTransferId, walk.skipped());
    }

    public void resume(UUID directoryTransferId) {
        if (directoryTransferService.getStatus(directoryTransferId) != GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Directory transfer is not paused: " + directoryTransferId, null);
        }

        DirectoryTransfer dt = directoryTransferService.get(directoryTransferId);
        directoryTransferService.markActive(directoryTransferId);

        GroupTransferContext ctx = new GroupTransferContext(
                directoryTransferId,
                dt.getTargetIp(),
                dt.getTargetPort(),
                directoryTransferService
        );
        groupTransferAsyncSender.sendAsync(ctx);
    }

    private WalkResult walkDirectory(Path root) {
        List<WalkResult.FileEntry> files = new ArrayList<>();
        List<String> emptyDirectories = new ArrayList<>();
        Set<Path> dirsWithFiles = new HashSet<>();
        Set<Path> skippedDirs = new HashSet<>();
        long[] totalSize = {0L};
        int[] skipped = {0};

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (SkipSourceEntryRule.shouldSkip(dir)) {
                        skipped[0]++;
                        skippedDirs.add(dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile() || SkipSourceEntryRule.shouldSkip(file)) {
                        skipped[0]++;
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        long size = Files.size(file);
                        files.add(new WalkResult.FileEntry(file, size));
                        totalSize[0] += size;
                        Path parent = file.getParent();
                        while (parent != null && !parent.equals(root)) {
                            dirsWithFiles.add(parent);
                            parent = parent.getParent();
                        }
                    } catch (IOException e) {
                        skipped[0]++;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    skipped[0]++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (exc != null) {
                        skipped[0]++;
                        return FileVisitResult.CONTINUE;
                    }
                    if (dir.equals(root) || skippedDirs.contains(dir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!dirsWithFiles.contains(dir)) {
                        emptyDirectories.add(root.relativize(dir).toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new FileTransferException("Failed to walk directory", e);
        }

        if (files.isEmpty() && skipped[0] == 0) {
            throw new FileTransferException("Directory contains no files to transfer", null);
        }

        return new WalkResult(
                files,
                emptyDirectories.isEmpty() ? Collections.emptyList() : emptyDirectories,
                totalSize[0],
                skipped[0]
        );
    }

    /**
     * Registers the directory payload on the target device.
     */
    private void registerOnTarget(
            InitiateDirectoryTransferRequest request,
            UUID directoryTransferId,
            UUID sourceDeviceId,
            String directoryName,
            WalkResult walk,
            List<ReceiveDirectoryRequest.FileEntry> fileEntries
    ) {
        transferHttpClient.registerDirectory(
                request.getTargetIp(),
                request.getTargetPort(),
                directoryTransferId,
                request.getDispatchId(),
                sourceDeviceId,
                directoryName,
                walk.files().size(),
                walk.totalSize(),
                walk.emptyDirectories(),
                fileEntries
        );
    }

    private record WalkResult(
            List<FileEntry> files, List<String> emptyDirectories, long totalSize, int skipped) {
        record FileEntry(Path absolutePath, long fileSize) {}
    }
}