package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateDirectoryTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateDirectoryTransferResponse;
import com.atamanahmet.beamlink.agent.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.http.HttpSender;
import com.atamanahmet.beamlink.agent.util.PathNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorySenderService {

    private final DirectoryTransferService directoryTransferService;
    private final AgentConfig agentConfig;
    private final GroupTransferAsyncSender groupTransferAsyncSender;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final HttpSender httpSender;

    public InitiateDirectoryTransferResponse initiate(InitiateDirectoryTransferRequest request) {
        String cleanedPath = PathNormalizer.normalize(request.getSourcePath());
        Path sourceDir = Paths.get(cleanedPath);

        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new FileTransferException("Directory not found: " + cleanedPath, null);
        }

        WalkResult walk = walkDirectory(sourceDir);
        UUID directoryTransferId = UUID.randomUUID();
        UUID sourceAgentId = agentService.getAgentId();
        String directoryName = sourceDir.getFileName().toString();

        directoryTransferService.create(
                directoryTransferId,
                sourceAgentId,
                request.getTargetAgentId(),
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
                    sourceAgentId,
                    request.getTargetAgentId(),
                    fileName,
                    entry.absolutePath().toString(),
                    entry.fileSize()
            );
            ft.setDirectoryTransferId(directoryTransferId);
            ft.setRelativePath(relativePath);
            ft.setTargetIp(request.getTargetIp());
            ft.setTargetPort(request.getTargetPort());
            ft.setExpiresAt(Instant.now().plusSeconds(agentConfig.getTransferExpiryHours() * 3600L));
            fileTransfers.add(ft);

            ReceiveDirectoryRequest.FileEntry fe = new ReceiveDirectoryRequest.FileEntry();
            fe.setTransferId(transferId);
            fe.setFileName(fileName);
            fe.setRelativePath(relativePath);
            fe.setFileSize(entry.fileSize());
            fileEntries.add(fe);
        }

        directoryTransferService.saveChildren(fileTransfers);
        registerOnTarget(request, directoryTransferId, sourceAgentId, directoryName, walk, fileEntries);
        directoryTransferService.markActive(directoryTransferId);

        GroupTransferContext ctx = new GroupTransferContext(
                directoryTransferId,
                request.getTargetIp(),
                request.getTargetPort(),
                directoryTransferService
        );
        groupTransferAsyncSender.sendAsync(ctx);

        log.info("Directory transfer initiated: {} → {} ({})",
                directoryName, request.getTargetAgentId(), directoryTransferId);

        return new InitiateDirectoryTransferResponse(directoryTransferId);
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
        long totalSize = 0L;

        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted().toList()) {
                if (path.equals(root)) continue;

                if (Files.isDirectory(path)) {
                    boolean hasFiles;
                    try (Stream<Path> dirContents = Files.walk(path)) {
                        hasFiles = dirContents
                                .filter(p -> !p.equals(path))
                                .anyMatch(Files::isRegularFile);
                    }
                    if (!hasFiles) {
                        emptyDirectories.add(root.relativize(path).toString());
                    }
                } else if (Files.isRegularFile(path)) {
                    if (!Files.isReadable(path)) {
                        throw new FileTransferException("File is not readable: " + path, null);
                    }
                    long size = Files.size(path);
                    files.add(new WalkResult.FileEntry(path, size));
                    totalSize += size;
                }
            }
        } catch (IOException e) {
            throw new FileTransferException("Failed to walk directory", e);
        }

        if (files.isEmpty()) {
            throw new FileTransferException("Directory contains no files to transfer", null);
        }

        return new WalkResult(
                files,
                emptyDirectories.isEmpty() ? Collections.emptyList() : emptyDirectories,
                totalSize
        );
    }

    private void registerOnTarget(
            InitiateDirectoryTransferRequest request,
            UUID directoryTransferId,
            UUID sourceAgentId,
            String directoryName,
            WalkResult walk,
            List<ReceiveDirectoryRequest.FileEntry> fileEntries
    ) {
        ReceiveDirectoryRequest payload = new ReceiveDirectoryRequest();
        payload.setDirectoryTransferId(directoryTransferId);
        payload.setDispatchId(request.getDispatchId());
        payload.setSourceAgentId(sourceAgentId);
        payload.setDirectoryName(directoryName);
        payload.setTotalFiles(walk.files().size());
        payload.setTotalSize(walk.totalSize());
        payload.setEmptyDirectories(walk.emptyDirectories());
        payload.setFiles(fileEntries);

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + request.getTargetIp() + ":" + request.getTargetPort() + "/api/transfers/receive-directory"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);
            if (response.statusCode() != 200) {
                throw new FileTransferException("Target rejected directory registration. Status: " + response.statusCode(), null);
            }
        } catch (FileTransferException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FileTransferException("Cannot reach target agent", e);
        } catch (IOException e) {
            throw new FileTransferException("Cannot reach target agent", e);
        }
    }

    private record WalkResult(List<FileEntry> files, List<String> emptyDirectories, long totalSize) {
        record FileEntry(Path absolutePath, long fileSize) {}
    }
}