package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.DeviceConfig;
import com.atamanahmet.beamlink.peer.domain.FileTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.peer.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.peer.dto.InitiateBatchTransferRequest;
import com.atamanahmet.beamlink.peer.dto.InitiateBatchTransferResponse;
import com.atamanahmet.beamlink.peer.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.peer.exception.FileTransferException;
import com.atamanahmet.beamlink.peer.exception.TransferProtocolException;
import com.atamanahmet.beamlink.peer.http.TransferHttpClient;
import com.atamanahmet.beamlink.peer.util.PathNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    private final DeviceConfig deviceConfig;
    private final GroupTransferAsyncSender groupTransferAsyncSender;
    private final DeviceService deviceService;
    private final TransferHttpClient transferHttpClient;

    public InitiateBatchTransferResponse initiate(InitiateBatchTransferRequest request) {
        if (request.getFilePaths() == null || request.getFilePaths().isEmpty()) {
            throw new FileTransferException("No file paths provided for batch transfer", null);
        }

        ValidatedList validated = validateFiles(request.getFilePaths());
        List<ValidatedFile> validatedFiles = validated.files();
        if (validatedFiles.isEmpty()) {
            return new InitiateBatchTransferResponse(null, validated.skipped());
        }
        UUID batchTransferId = UUID.randomUUID();
        UUID sourceDeviceId = deviceService.getDeviceId();
        long totalSize = validatedFiles.stream().mapToLong(vf -> vf.fileSize).sum();

        batchTransferService.create(
                batchTransferId,
                sourceDeviceId,
                request.getTargetDeviceId(),
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
                    sourceDeviceId,
                    request.getTargetDeviceId(),
                    vf.path.getFileName().toString(),
                    vf.path.toString(),
                    vf.fileSize
            );
            ft.setBatchTransferId(batchTransferId);
            ft.setTargetIp(request.getTargetIp());
            ft.setTargetPort(request.getTargetPort());
            ft.setExpiresAt(Instant.now().plusSeconds(deviceConfig.getTransferExpiryHours() * 3600L));
            fileTransfers.add(ft);

            ReceiveBatchRequest.FileEntry fe = new ReceiveBatchRequest.FileEntry();
            fe.setTransferId(transferId);
            fe.setFileName(vf.path.getFileName().toString());
            fe.setFileSize(vf.fileSize);
            fileEntries.add(fe);
        }

        batchTransferService.saveChildren(fileTransfers);
        try {
            transferHttpClient.registerBatch(
                    request.getTargetIp(),
                    request.getTargetPort(),
                    batchTransferId,
                    sourceDeviceId,
                    request.getDispatchId(),
                    validatedFiles.size(),
                    totalSize,
                    fileEntries);
        } catch (TransferProtocolException e) {
            if (RegisterRejectRule.keepFailed(e.getCode())) {
                failBatchRegister(fileTransfers, batchTransferId, e.getMessage());
            }
            throw e;
        }
        batchTransferService.markActive(batchTransferId);

        GroupTransferContext ctx = new GroupTransferContext(
                batchTransferId,
                request.getTargetIp(),
                request.getTargetPort(),
                batchTransferService
        );
        groupTransferAsyncSender.sendAsync(ctx);

        log.info("Batch transfer initiated: {} files → {} ({})",
                validatedFiles.size(), request.getTargetDeviceId(), batchTransferId);

        return new InitiateBatchTransferResponse(batchTransferId, validated.skipped());
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

    private ValidatedList validateFiles(List<String> filePaths) {
        List<ValidatedFile> result = new ArrayList<>();
        int skipped = 0;

        for (String raw : filePaths) {
            String cleaned = PathNormalizer.normalize(raw);
            Path path = Paths.get(cleaned);

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new FileTransferException("File not found: " + cleaned, null);
            }

            if (SkipSourceEntryRule.shouldSkip(path)) {
                skipped++;
                continue;
            }

            long size;
            try {
                size = Files.size(path);
            } catch (IOException e) {
                skipped++;
                continue;
            }

            result.add(new ValidatedFile(path, size));
        }

        return new ValidatedList(result, skipped);
    }

    /**
     * Marks the batch and children FAILED so the UI can show the peer message.
     */
    private void failBatchRegister(
            List<FileTransfer> fileTransfers, UUID batchTransferId, String reason) {
        for (FileTransfer ft : fileTransfers) {
            ft.setStatus(TransferStatus.FAILED);
            ft.setFailureReason(reason);
        }
        batchTransferService.saveChildren(fileTransfers);
        batchTransferService.markFailed(batchTransferId);
    }

    private record ValidatedFile(Path path, long fileSize) {}

    private record ValidatedList(List<ValidatedFile> files, int skipped) {}
}