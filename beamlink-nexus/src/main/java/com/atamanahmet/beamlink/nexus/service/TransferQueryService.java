package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.BatchTransfer;
import com.atamanahmet.beamlink.nexus.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.TransferListPage;
import com.atamanahmet.beamlink.nexus.dto.TransferSummary;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.mapper.TransferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferQueryService {

    private final FileTransferService fileTransferService;
    private final BatchTransferService batchTransferService;
    private final DirectoryTransferService directoryTransferService;
    private final TransferMapper transferMapper;
    private final NexusResumePolicyStore resumePolicyStore;
    private final TransferSideResolver sideResolver;

    public List<TransferSummary> listAll() {
        List<TransferSummary> result = new ArrayList<>();

        fileTransferService.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(ft -> ft.getBatchTransferId() == null && ft.getDirectoryTransferId() == null)
                .map(this::mapSingle)
                .forEach(result::add);

        batchTransferService.getAll()
                .stream()
                .map(this::mapBatch)
                .forEach(result::add);

        directoryTransferService.getAll()
                .stream()
                .map(this::mapDirectory)
                .forEach(result::add);

        result.sort(Comparator.comparing(TransferSummary::getCreatedAt).reversed());
        return result;
    }

    /**
     * Returns one page of the full transfer list.
     */
    public TransferListPage listPage(int page, int size) {
        List<TransferSummary> all = listAll();
        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return TransferListPage.builder()
                .items(all.subList(from, to))
                .total(total)
                .hasMore(to < total)
                .build();
    }

    public TransferSummary getSingle(UUID id) {
        return mapSingle(
                fileTransferService.findByTransferId(id)
                        .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null))
        );
    }

    public TransferSummary getBatch(UUID id) {
        return mapBatch(
                batchTransferService.findById(id)
                        .orElseThrow(() -> new FileTransferException("Batch transfer not found: " + id, null))
        );
    }

    public TransferSummary getDirectory(UUID id) {
        return mapDirectory(
                directoryTransferService.findById(id)
                        .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null))
        );
    }

    public List<TransferSummary> getBatchFiles(UUID batchId) {
        return fileTransferService.findByBatchTransferId(batchId)
                .stream()
                .map(this::mapSingle)
                .toList();
    }

    public List<TransferSummary> getDirectoryFiles(UUID directoryId) {
        return fileTransferService.findByDirectoryTransferId(directoryId)
                .stream()
                .map(this::mapSingle)
                .toList();
    }

    private TransferSummary mapSingle(FileTransfer ft) {
        return transferMapper.fromSingle(ft, canResumeSingle(ft));
    }

    private TransferSummary mapBatch(BatchTransfer bt) {
        List<FileTransfer> children = fileTransferService.findByBatchTransferId(bt.getBatchTransferId());

        long confirmedBytes = children.stream().mapToLong(FileTransfer::getConfirmedOffset).sum();

        String firstName = children.stream()
                .findFirst()
                .map(FileTransfer::getFileName)
                .orElse("batch");

        return transferMapper.fromBatch(bt, confirmedBytes, firstName, canResumeGroup(
                bt.getStatus().name(),
                bt.getPausedBy(),
                bt.getSourceDeviceId(),
                bt.getTargetDeviceId(),
                bt.getTargetIp()));
    }

    private TransferSummary mapDirectory(DirectoryTransfer dt) {
        long confirmedBytes = fileTransferService
                .findByDirectoryTransferId(dt.getDirectoryTransferId())
                .stream()
                .mapToLong(FileTransfer::getConfirmedOffset)
                .sum();

        return transferMapper.fromDirectory(dt, confirmedBytes, canResumeGroup(
                dt.getStatus().name(),
                dt.getPausedBy(),
                dt.getSourceDeviceId(),
                dt.getTargetDeviceId(),
                dt.getTargetIp()));
    }

    private boolean canResumeSingle(FileTransfer ft) {
        if (ft.getStatus() != TransferStatus.PAUSED) {
            return false;
        }
        PauseOwner side = sideResolver.resolve(
                ft.getSourceDeviceId(), ft.getTargetDeviceId(), ft.getTargetIp());
        return ResumePolicy.canResume(resumePolicyStore.getMode(), side, ft.getPausedBy());
    }

    private boolean canResumeGroup(
            String statusName,
            PauseOwner pausedBy,
            UUID sourceDeviceId,
            UUID targetDeviceId,
            String targetIp) {
        if (!"PAUSED".equals(statusName)) {
            return false;
        }
        PauseOwner side = sideResolver.resolve(sourceDeviceId, targetDeviceId, targetIp);
        return ResumePolicy.canResume(resumePolicyStore.getMode(), side, pausedBy);
    }
}
