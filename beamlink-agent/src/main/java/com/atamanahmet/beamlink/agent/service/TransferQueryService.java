package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.TransferSummary;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.mapper.TransferMapper;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferQueryService {

    private final FileTransferRepository fileTransferRepository;
    private final BatchTransferRepository batchTransferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final TransferMapper transferMapper;
    private final AgentResumePolicyHolder resumePolicyHolder;
    private final TransferSideResolver sideResolver;

    public List<TransferSummary> listAll() {
        List<TransferSummary> result = new ArrayList<>();

        fileTransferRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(ft -> ft.getBatchTransferId() == null && ft.getDirectoryTransferId() == null)
                .map(this::mapSingle)
                .forEach(result::add);

        batchTransferRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapBatch)
                .forEach(result::add);

        directoryTransferRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapDirectory)
                .forEach(result::add);

        result.sort(Comparator.comparing(TransferSummary::getCreatedAt).reversed());
        return result;
    }

    public TransferSummary getSingle(UUID id) {
        return mapSingle(
                fileTransferRepository.findByTransferId(id)
                        .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null))
        );
    }

    public TransferSummary getBatch(UUID id) {
        return mapBatch(
                batchTransferRepository.findById(id)
                        .orElseThrow(() -> new FileTransferException("Batch transfer not found: " + id, null))
        );
    }

    public TransferSummary getDirectory(UUID id) {
        return mapDirectory(
                directoryTransferRepository.findById(id)
                        .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null))
        );
    }

    public List<TransferSummary> getBatchFiles(UUID batchId) {
        return fileTransferRepository.findByBatchTransferId(batchId)
                .stream()
                .map(this::mapSingle)
                .toList();
    }

    public List<TransferSummary> getDirectoryFiles(UUID directoryId) {
        return fileTransferRepository.findByDirectoryTransferId(directoryId)
                .stream()
                .map(this::mapSingle)
                .toList();
    }

    private TransferSummary mapSingle(FileTransfer ft) {
        return transferMapper.fromSingle(ft, canResumeSingle(ft));
    }

    private TransferSummary mapBatch(BatchTransfer bt) {
        List<FileTransfer> children = fileTransferRepository.findByBatchTransferId(bt.getBatchTransferId());

        long confirmedBytes = children.stream().mapToLong(FileTransfer::getConfirmedOffset).sum();

        String firstName = children.stream()
                .findFirst()
                .map(FileTransfer::getFileName)
                .orElse("batch");

        return transferMapper.fromBatch(bt, confirmedBytes, firstName, canResumeGroup(
                bt.getStatus().name(),
                bt.getPausedBy(),
                bt.getSourceAgentId(),
                bt.getTargetAgentId(),
                bt.getTargetIp()));
    }

    private TransferSummary mapDirectory(DirectoryTransfer dt) {
        long confirmedBytes = fileTransferRepository
                .findByDirectoryTransferId(dt.getDirectoryTransferId())
                .stream()
                .mapToLong(FileTransfer::getConfirmedOffset)
                .sum();

        return transferMapper.fromDirectory(dt, confirmedBytes, canResumeGroup(
                dt.getStatus().name(),
                dt.getPausedBy(),
                dt.getSourceAgentId(),
                dt.getTargetAgentId(),
                dt.getTargetIp()));
    }

    private boolean canResumeSingle(FileTransfer ft) {
        if (ft.getStatus() != TransferStatus.PAUSED) {
            return false;
        }
        PauseOwner side = sideResolver.resolve(
                ft.getSourceAgentId(), ft.getTargetAgentId(), ft.getTargetIp());
        return ResumePolicy.canResume(resumePolicyHolder.getMode(), side, ft.getPausedBy());
    }

    private boolean canResumeGroup(
            String statusName,
            PauseOwner pausedBy,
            UUID sourceAgentId,
            UUID targetAgentId,
            String targetIp) {
        if (!"PAUSED".equals(statusName)) {
            return false;
        }
        PauseOwner side = sideResolver.resolve(sourceAgentId, targetAgentId, targetIp);
        return ResumePolicy.canResume(resumePolicyHolder.getMode(), side, pausedBy);
    }
}
