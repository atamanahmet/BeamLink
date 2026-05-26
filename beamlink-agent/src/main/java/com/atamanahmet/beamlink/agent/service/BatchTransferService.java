package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchTransferService implements GroupTransferService {

    private final BatchTransferRepository batchTransferRepository;
    private final FileTransferRepository fileTransferRepository;

    public BatchTransfer create(
            UUID batchTransferId,
            UUID sourceAgentId,
            UUID targetAgentId,
            String targetIp,
            int targetPort,
            int totalFiles,
            long totalSize,
            UUID dispatchId
    ) {
        BatchTransfer bt = BatchTransfer.initiate(
                batchTransferId, dispatchId, sourceAgentId, targetAgentId,
                targetIp, targetPort, totalFiles, totalSize);
        return batchTransferRepository.save(bt);
    }

    public BatchTransfer get(UUID batchTransferId) {
        return batchTransferRepository.findByBatchTransferId(batchTransferId)
                .orElseThrow(() -> new FileTransferException(
                        "Batch transfer not found: " + batchTransferId, null));
    }

    public List<BatchTransfer> getAll() {
        return batchTransferRepository.findAllByOrderByCreatedAtDesc();
    }

    public void saveChildren(List<FileTransfer> fileTransfers) {
        fileTransferRepository.saveAll(fileTransfers);
    }

    @Override
    public UUID getGroupId() {
        throw new UnsupportedOperationException(
                "Use getStatus(UUID) and getOrderedQueue(UUID) directly");
    }

    @Override
    public GroupTransferStatus getStatus(UUID groupId) {
        return get(groupId).getStatus();
    }

    @Override
    public List<FileTransfer> getOrderedQueue(UUID groupId) {
        List<FileTransfer> children =
                fileTransferRepository.findByBatchTransferId(groupId);

        List<FileTransfer> queue = new ArrayList<>();
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.PAUSED)
                .forEach(queue::add);
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.ACTIVE
                        || ft.getStatus() == TransferStatus.PENDING)
                .forEach(queue::add);
        return queue;
    }

    @Override
    public List<FileTransfer> getAllChildren(UUID groupId) {
        return fileTransferRepository.findByBatchTransferId(groupId);
    }

    @Override
    public void markActive(UUID groupId) {
        BatchTransfer bt = get(groupId);
        bt.setStatus(GroupTransferStatus.ACTIVE);
        batchTransferRepository.save(bt);
    }

    @Override
    public void markCompleted(UUID groupId) {
        BatchTransfer bt = get(groupId);
        bt.setStatus(GroupTransferStatus.COMPLETED);
        bt.setCompletedAt(Instant.now());
        batchTransferRepository.save(bt);
    }

    @Override
    public void markPartial(UUID groupId) {
        BatchTransfer bt = get(groupId);
        bt.setStatus(GroupTransferStatus.PARTIAL);
        batchTransferRepository.save(bt);
    }

    @Override
    public void markFailed(UUID groupId) {
        BatchTransfer bt = get(groupId);
        bt.setStatus(GroupTransferStatus.FAILED);
        batchTransferRepository.save(bt);
    }

    @Override
    public void markCancelled(UUID groupId) {
        BatchTransfer bt = get(groupId);
        bt.setStatus(GroupTransferStatus.CANCELLED);
        batchTransferRepository.save(bt);
    }
}