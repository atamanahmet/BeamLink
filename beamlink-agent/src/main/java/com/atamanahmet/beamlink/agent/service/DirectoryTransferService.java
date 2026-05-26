package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectoryTransferService implements GroupTransferService {

    private final DirectoryTransferRepository directoryTransferRepository;
    private final FileTransferRepository fileTransferRepository;

    public DirectoryTransfer create(
            UUID directoryTransferId,
            UUID sourceAgentId,
            UUID targetAgentId,
            String targetIp,
            int targetPort,
            String directoryName,
            String sourcePath,
            int totalFiles,
            long totalSize,
            List<String> emptyDirectories,
            UUID dispatchId
    ) {
        DirectoryTransfer dt = DirectoryTransfer.initiate(
                directoryTransferId, dispatchId, sourceAgentId, targetAgentId,
                targetIp, targetPort, directoryName, sourcePath,
                totalFiles, totalSize, emptyDirectories);
        return directoryTransferRepository.save(dt);
    }

    public DirectoryTransfer get(UUID directoryTransferId) {
        return directoryTransferRepository
                .findByDirectoryTransferId(directoryTransferId)
                .orElseThrow(() -> new FileTransferException(
                        "Directory transfer not found: " + directoryTransferId, null));
    }

    public List<DirectoryTransfer> getAll() {
        return directoryTransferRepository.findAllByOrderByCreatedAtDesc();
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
                fileTransferRepository.findByDirectoryTransferId(groupId);

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
        return fileTransferRepository.findByDirectoryTransferId(groupId);
    }

    @Override
    public void markActive(UUID groupId) {
        DirectoryTransfer dt = get(groupId);
        dt.setStatus(GroupTransferStatus.ACTIVE);
        directoryTransferRepository.save(dt);
    }

    @Override
    public void markCompleted(UUID groupId) {
        DirectoryTransfer dt = get(groupId);
        dt.setStatus(GroupTransferStatus.COMPLETED);
        dt.setCompletedAt(Instant.now());
        directoryTransferRepository.save(dt);
    }

    @Override
    public void markPartial(UUID groupId) {
        DirectoryTransfer dt = get(groupId);
        dt.setStatus(GroupTransferStatus.PARTIAL);
        directoryTransferRepository.save(dt);
    }

    @Override
    public void markFailed(UUID groupId) {
        DirectoryTransfer dt = get(groupId);
        dt.setStatus(GroupTransferStatus.FAILED);
        directoryTransferRepository.save(dt);
    }

    @Override
    public void markCancelled(UUID groupId) {
        DirectoryTransfer dt = get(groupId);
        dt.setStatus(GroupTransferStatus.CANCELLED);
        directoryTransferRepository.save(dt);
    }

    public void saveChildren(List<FileTransfer> fileTransfers) {
        fileTransferRepository.saveAll(fileTransfers);
    }
}