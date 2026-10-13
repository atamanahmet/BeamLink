package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.http.TransferHttpClient;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferManagementService {

    private final TransferSenderService transferSenderService;
    private final BatchSenderService batchSenderService;
    private final DirectorySenderService directorySenderService;
    private final BatchTransferRepository batchTransferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final FileTransferRepository fileTransferRepository;
    private final TransferSender asyncSender;
    private final TransferHttpClient transferHttpClient;


    public void pauseSingle(UUID id) {
        fileTransferRepository.findByTransferId(id).ifPresent(ft -> {
            if (ft.getStatus() == TransferStatus.ACTIVE) {
                ft.setStatus(TransferStatus.PAUSED);
                fileTransferRepository.save(ft);
            }
        });
    }

    public void resumeSingle(UUID id) {
        transferSenderService.resume(id);
        FileTransfer ft = fileTransferRepository.findByTransferId(id)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null));
        asyncSender.sendAsync(id, ft.getTargetIp(), ft.getTargetPort());
    }

    public void cancelSingle(UUID id) {
        transferSenderService.cancel(id);
        fileTransferRepository.findByTransferId(id).ifPresent(this::notifyPeerCancel);
    }

    /** Stops queue loop, stops active child at next chunk boundary. */
    @Transactional
    public void cancelBatch(UUID id) {
        BatchTransfer bt = batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        bt.setStatus(GroupTransferStatus.CANCELLED);
        batchTransferRepository.save(bt);

        fileTransferRepository.findByBatchTransferId(id)
                .forEach(ft -> {
                    if (ft.getStatus() == TransferStatus.ACTIVE
                            || ft.getStatus() == TransferStatus.PAUSED) {
                        ft.setStatus(TransferStatus.CANCELLED);
                        fileTransferRepository.save(ft);
                    }
                    notifyPeerCancel(ft);
                });
    }

    /** Stops queue loop, stops active child at next chunk boundary. */
    @Transactional
    public void cancelDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        dt.setStatus(GroupTransferStatus.CANCELLED);
        directoryTransferRepository.save(dt);

        fileTransferRepository.findByDirectoryTransferId(id)
                .forEach(ft -> {
                    if (ft.getStatus() == TransferStatus.ACTIVE
                            || ft.getStatus() == TransferStatus.PAUSED) {
                        ft.setStatus(TransferStatus.CANCELLED);
                        fileTransferRepository.save(ft);
                    }
                    notifyPeerCancel(ft);
                });
    }

    public void deleteSingle(UUID id) {
        transferSenderService.delete(id);
    }


    /** Stops queue loop, stops active child at next chunk boundary. */
    @Transactional
    public void pauseBatch(UUID id) {
        BatchTransfer bt = batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        if (bt.getStatus() != GroupTransferStatus.ACTIVE) {
            throw new FileTransferException("Batch is not active: " + bt.getStatus(), null);
        }

        bt.setStatus(GroupTransferStatus.PAUSED);
        batchTransferRepository.save(bt);

        fileTransferRepository.findByBatchTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.PAUSED);
                    fileTransferRepository.save(ft);
                });
    }

    public void resumeBatch(UUID id) {
        batchSenderService.resume(id);
    }

    @Transactional
    public void deleteBatch(UUID id) {
        BatchTransfer bt = batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        GroupTransferStatus s = bt.getStatus();
        if (s == GroupTransferStatus.ACTIVE || s == GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Cannot delete an in-progress batch", null);
        }

        batchTransferRepository.delete(bt);
    }


    /** Stops queue loop, stops active child at next chunk boundary. */
    @Transactional
    public void pauseDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        if (dt.getStatus() != GroupTransferStatus.ACTIVE) {
            throw new FileTransferException("Directory transfer is not active: " + dt.getStatus(), null);
        }

        dt.setStatus(GroupTransferStatus.PAUSED);
        directoryTransferRepository.save(dt);

        fileTransferRepository.findByDirectoryTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.PAUSED);
                    fileTransferRepository.save(ft);
                });
    }

    public void resumeDirectory(UUID id) {
        directorySenderService.resume(id);
    }

    @Transactional
    public void deleteDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        GroupTransferStatus s = dt.getStatus();
        if (s == GroupTransferStatus.ACTIVE || s == GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Cannot delete an in-progress directory transfer", null);
        }

        directoryTransferRepository.delete(dt);
    }

    /**
     * Notifies peer node to cancel their side. Fire-and-forget, local cancel already done.
     */
    private void notifyPeerCancel(FileTransfer ft) {
        String peerIp;
        int peerPort;

        if (ft.getTargetIp() != null && !ft.getTargetIp().isBlank()
                && ft.getTargetPort() != null) {
            peerIp = ft.getTargetIp();
            peerPort = ft.getTargetPort();
        } else if (ft.getSourceIp() != null && !ft.getSourceIp().isBlank()
                && ft.getSourcePort() != null) {
            peerIp = ft.getSourceIp();
            peerPort = ft.getSourcePort();
        } else {
            log.warn("No peer address stored for transfer {}, skipping cancel notify", ft.getTransferId());
            return;
        }

        try {
            transferHttpClient.notifyCancelToPeer(peerIp, peerPort, ft.getTransferId());
        } catch (Exception e) {
            log.warn("Could not notify peer of cancel for transfer {}: {}", ft.getTransferId(), e.getMessage());
        }
    }
}