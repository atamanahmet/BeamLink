package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileTransferService {

    private final FileTransferRepository fileTransferRepository;

    public FileTransfer save(FileTransfer transfer) {
        return fileTransferRepository.save(transfer);
    }

    public List<FileTransfer> saveAll(Iterable<FileTransfer> transfers) {
        return fileTransferRepository.saveAll(transfers);
    }

    public void delete(FileTransfer transfer) {
        fileTransferRepository.delete(transfer);
    }

    /**
     * Deletes every file transfer row.
     */
    public void deleteAll() {
        fileTransferRepository.deleteAll();
    }

    public Optional<FileTransfer> findByTransferId(UUID transferId) {
        return fileTransferRepository.findByTransferId(transferId);
    }

    public Optional<TransferStatus> findStatusByTransferId(UUID transferId) {
        return fileTransferRepository.findStatusByTransferId(transferId);
    }

    public List<FileTransfer> findByStatus(TransferStatus status) {
        return fileTransferRepository.findByStatus(status);
    }

    public long countByStatus(TransferStatus status) {
        return fileTransferRepository.countByStatus(status);
    }

    public List<FileTransfer> findByStatusAndExpiresAtBefore(TransferStatus status, Instant now) {
        return fileTransferRepository.findByStatusAndExpiresAtBefore(status, now);
    }

    public List<FileTransfer> findAllByOrderByCreatedAtDesc() {
        return fileTransferRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<FileTransfer> findByTargetDeviceIdAndStatus(UUID deviceId, TransferStatus status) {
        return fileTransferRepository.findByTargetDeviceIdAndStatus(deviceId, status);
    }

    public List<FileTransfer> findByDirectoryTransferId(UUID directoryTransferId) {
        return fileTransferRepository.findByDirectoryTransferId(directoryTransferId);
    }

    public List<FileTransfer> findByDirectoryTransferIdAndStatus(
            UUID directoryTransferId, TransferStatus status) {
        return fileTransferRepository.findByDirectoryTransferIdAndStatus(directoryTransferId, status);
    }

    public List<FileTransfer> findByBatchTransferId(UUID batchTransferId) {
        return fileTransferRepository.findByBatchTransferId(batchTransferId);
    }

    public List<FileTransfer> findByBatchTransferIdAndStatus(
            UUID batchTransferId, TransferStatus status) {
        return fileTransferRepository.findByBatchTransferIdAndStatus(batchTransferId, status);
    }
}
