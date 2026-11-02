package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.TransferType;
import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferDispatchService {

    private final TransferSenderService transferSenderService;
    private final BatchSenderService batchSenderService;
    private final DirectorySenderService directorySenderService;
    private final TransferSender asyncSender;

    /** One dispatchId groups all transfers from this single send action */
    public InitiateSendResponse dispatch(InitiateSendRequest request) {
        if (request.getPaths() == null || request.getPaths().isEmpty()) {
            throw new FileTransferException("No paths provided", null);
        }

        List<String> filePaths = new ArrayList<>();
        List<String> directoryPaths = new ArrayList<>();
        int skipped = classifyPaths(request.getPaths(), filePaths, directoryPaths);

        UUID dispatchId = UUID.randomUUID();

        List<DispatchResultItem> results = new ArrayList<>();
        skipped += dispatchFiles(request, filePaths, results, dispatchId);
        skipped += dispatchDirectories(request, directoryPaths, results, dispatchId);

        log.info("Dispatch complete: {} transfer(s) initiated, {} skipped", results.size(), skipped);
        return new InitiateSendResponse(results, skipped);
    }

    /**
     * Separates paths into files and directories. Unreadable IOException is skipped, not thrown.
     */
    private int classifyPaths(List<String> paths,
                               List<String> filePaths,
                               List<String> directoryPaths) {
        int skipped = 0;
        for (String raw : paths) {
            Path path = Paths.get(raw);
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                if (SkipSourceEntryRule.shouldSkip(path)) {
                    skipped++;
                    continue;
                }
                if (attrs.isDirectory()) {
                    directoryPaths.add(raw);
                } else if (attrs.isRegularFile()) {
                    filePaths.add(raw);
                } else {
                    throw new FileTransferException("Not a file or directory: " + raw, null);
                }
            } catch (NoSuchFileException e) {
                throw new FileTransferException("Path does not exist: " + raw, e);
            } catch (IOException e) {
                skipped++;
            }
        }
        return skipped;
    }

    /**
     * Single file: SINGLE, multiple files: BATCH.
     * single transfer don't need dispatchId
     */
    private int dispatchFiles(InitiateSendRequest request,
                               List<String> filePaths,
                               List<DispatchResultItem> results,
                               UUID dispatchId) {
        if (filePaths.isEmpty()) return 0;

        if (filePaths.size() == 1) {
            InitiateTransferRequest req = new InitiateTransferRequest();
            req.setFilePath(filePaths.get(0));
            req.setTargetDeviceId(request.getTargetDeviceId());
            req.setTargetIp(request.getTargetIp());
            req.setTargetPort(request.getTargetPort());

            UUID id = transferSenderService.initiate(req).getTransferId();
            asyncSender.sendAsync(id, request.getTargetIp(), request.getTargetPort());
            results.add(new DispatchResultItem(id, TransferType.SINGLE, dispatchId));
            return 0;
        }

        InitiateBatchTransferRequest req = new InitiateBatchTransferRequest();
        req.setFilePaths(filePaths);
        req.setTargetDeviceId(request.getTargetDeviceId());
        req.setTargetIp(request.getTargetIp());
        req.setTargetPort(request.getTargetPort());
        req.setDispatchId(dispatchId);

        InitiateBatchTransferResponse batch = batchSenderService.initiate(req);
        if (batch.getBatchTransferId() != null) {
            results.add(new DispatchResultItem(
                    batch.getBatchTransferId(), TransferType.BATCH, dispatchId));
        }
        return batch.getSkippedCount();
    }

    /**
     * One dispatch per directory.
     */
    private int dispatchDirectories(InitiateSendRequest request,
                                     List<String> directoryPaths,
                                     List<DispatchResultItem> results,
                                     UUID dispatchId) {
        int skipped = 0;
        for (String dirPath : directoryPaths) {
            InitiateDirectoryTransferRequest req = new InitiateDirectoryTransferRequest();
            req.setSourcePath(dirPath);
            req.setTargetDeviceId(request.getTargetDeviceId());
            req.setTargetIp(request.getTargetIp());
            req.setTargetPort(request.getTargetPort());
            req.setDispatchId(dispatchId);

            InitiateDirectoryTransferResponse dir = directorySenderService.initiate(req);
            skipped += dir.getSkippedCount();
            if (dir.getDirectoryTransferId() != null) {
                results.add(new DispatchResultItem(
                        dir.getDirectoryTransferId(), TransferType.DIRECTORY, dispatchId));
            }
        }
        return skipped;
    }
}