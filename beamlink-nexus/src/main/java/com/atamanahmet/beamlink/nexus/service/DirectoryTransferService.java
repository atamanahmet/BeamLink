package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.repository.DirectoryTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DirectoryTransferService implements GroupTransferService {

    private final DirectoryTransferRepository directoryTransferRepository;
    private final FileTransferService fileTransferService;

    public DirectoryTransfer create(
            UUID directoryTransferId,
            UUID sourceDeviceId,
            UUID targetDeviceId,
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
                directoryTransferId, dispatchId, sourceDeviceId, targetDeviceId,
                targetIp, targetPort, directoryName, sourcePath,
                totalFiles, totalSize, emptyDirectories);
        return directoryTransferRepository.save(dt);
    }

    public Optional<DirectoryTransfer> findById(UUID directoryTransferId) {
        return directoryTransferRepository.findById(directoryTransferId);
    }

    public DirectoryTransfer get(UUID directoryTransferId) {
        return directoryTransferRepository
                .findByDirectoryTransferId(directoryTransferId)
                .orElseThrow(() -> new FileTransferException(
                        "Directory transfer not found: " + directoryTransferId, null));
    }

    public DirectoryTransfer save(DirectoryTransfer directoryTransfer) {
        return directoryTransferRepository.save(directoryTransfer);
    }

    public void delete(DirectoryTransfer directoryTransfer) {
        directoryTransferRepository.delete(directoryTransfer);
    }

    public List<DirectoryTransfer> findAll() {
        return directoryTransferRepository.findAll();
    }

    public List<DirectoryTransfer> getAll() {
        return directoryTransferRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Deletes every directory transfer row.
     */
    public void deleteAll() {
        directoryTransferRepository.deleteAll();
    }

    public long countByStatus(GroupTransferStatus status) {
        return directoryTransferRepository.countByStatus(status);
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
                fileTransferService.findByDirectoryTransferId(groupId);

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
        return fileTransferService.findByDirectoryTransferId(groupId);
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

    @Override
    public void applyChildStatusRollup(UUID groupId) {
        DirectoryTransfer dt = get(groupId);
        GroupTransferStatus status = GroupStatusRollup.fromChildren(
                getAllChildren(groupId).stream().map(FileTransfer::getStatus).toList());
        dt.setStatus(status);
        if (status == GroupTransferStatus.COMPLETED) {
            dt.setCompletedAt(Instant.now());
        }
        directoryTransferRepository.save(dt);
    }

    public void saveChildren(List<FileTransfer> fileTransfers) {
        fileTransferService.saveAll(fileTransfers);
    }
}