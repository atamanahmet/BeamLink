package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateBatchTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateBatchTransferResponse;
import com.atamanahmet.beamlink.agent.dto.ReceiveBatchRequest;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchSenderService {

    private final BatchTransferService batchTransferService;
    private final AgentConfig agentConfig;
    private final GroupTransferAsyncSender groupTransferAsyncSender;
    private final ObjectMapper objectMapper;
    private final AgentService agentService;
    private final HttpSender httpSender;

    public InitiateBatchTransferResponse initiate(InitiateBatchTransferRequest request) {
        if (request.getFilePaths() == null || request.getFilePaths().isEmpty()) {
            throw new FileTransferException("No file paths provided for batch transfer", null);
        }

        List<ValidatedFile> validatedFiles = validateFiles(request.getFilePaths());
        UUID batchTransferId = UUID.randomUUID();
        UUID sourceAgentId = agentService.getAgentId();
        long totalSize = validatedFiles.stream().mapToLong(vf -> vf.fileSize).sum();

        batchTransferService.create(
                batchTransferId,
                sourceAgentId,
                request.getTargetAgentId(),
                request.getTargetIp(),
                request.getTargetPort(),
                validatedFiles.size(),
                totalSize,
                request.getDispatchId()
        );

        List<FileTransfer> fileTransfers = new ArrayList<>();
        List<ReceiveBatchRequest.FileEntry> fileEntries = new ArrayList<>();

        for (ValidatedFile vf : validatedFiles) {
            UUID transferId = UUID.randomUUID();

            FileTransfer ft = FileTransfer.initiate(
                    transferId,
                    sourceAgentId,
                    request.getTargetAgentId(),
                    vf.path.getFileName().toString(),
                    vf.path.toString(),
                    vf.fileSize
            );
            ft.setBatchTransferId(batchTransferId);
            ft.setTargetIp(request.getTargetIp());
            ft.setTargetPort(request.getTargetPort());
            ft.setExpiresAt(Instant.now().plusSeconds(agentConfig.getTransferExpiryHours() * 3600L));
            fileTransfers.add(ft);

            ReceiveBatchRequest.FileEntry fe = new ReceiveBatchRequest.FileEntry();
            fe.setTransferId(transferId);
            fe.setFileName(vf.path.getFileName().toString());
            fe.setFileSize(vf.fileSize);
            fileEntries.add(fe);
        }

        batchTransferService.saveChildren(fileTransfers);
        registerOnTarget(request, batchTransferId, sourceAgentId, validatedFiles.size(), totalSize, fileEntries);
        batchTransferService.markActive(batchTransferId);

        GroupTransferContext ctx = new GroupTransferContext(
                batchTransferId,
                request.getTargetIp(),
                request.getTargetPort(),
                batchTransferService
        );
        groupTransferAsyncSender.sendAsync(ctx);

        log.info("Batch transfer initiated: {} files → {} ({})",
                validatedFiles.size(), request.getTargetAgentId(), batchTransferId);

        return new InitiateBatchTransferResponse(batchTransferId);
    }

    public void resume(UUID batchTransferId) {
        var bt = batchTransferService.get(batchTransferId);
        if (bt.getStatus() != GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Batch transfer is not paused, current status: " + bt.getStatus(), null);
        }

        batchTransferService.markActive(batchTransferId);

        GroupTransferContext ctx = new GroupTransferContext(
                batchTransferId,
                bt.getTargetIp(),
                bt.getTargetPort(),
                batchTransferService
        );
        groupTransferAsyncSender.sendAsync(ctx);
    }

    private List<ValidatedFile> validateFiles(List<String> filePaths) {
        List<ValidatedFile> result = new ArrayList<>();

        for (String raw : filePaths) {
            String cleaned = PathNormalizer.normalize(raw);
            Path path = Paths.get(cleaned);

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new FileTransferException("File not found: " + cleaned, null);
            }

            if (!Files.isReadable(path)) {
                throw new FileTransferException("File not readable: " + cleaned, null);
            }

            long size;
            try {
                size = Files.size(path);
            } catch (IOException e) {
                throw new FileTransferException("Cannot read file size: " + cleaned, e);
            }

            result.add(new ValidatedFile(path, size));
        }

        return result;
    }

    private void registerOnTarget(
            InitiateBatchTransferRequest request,
            UUID batchTransferId,
            UUID sourceAgentId,
            int totalFiles,
            long totalSize,
            List<ReceiveBatchRequest.FileEntry> fileEntries
    ) {
        ReceiveBatchRequest payload = new ReceiveBatchRequest();
        payload.setBatchTransferId(batchTransferId);
        payload.setDispatchId(request.getDispatchId());
        payload.setSourceAgentId(sourceAgentId);
        payload.setTotalFiles(totalFiles);
        payload.setTotalSize(totalSize);
        payload.setFiles(fileEntries);

        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + request.getTargetIp() + ":" + request.getTargetPort() + "/api/transfers/receive-batch"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpSender.send(httpRequest);
            if (response.statusCode() != 200) {
                throw new FileTransferException("Target rejected batch registration. Status: " + response.statusCode(), null);
            }
        } catch (FileTransferException e) {
            throw e;
        } catch (InterruptedException | IOException e) {
            throw new FileTransferException("Cannot reach target agent", e);
        }
    }

    private record ValidatedFile(Path path, long fileSize) {}
}