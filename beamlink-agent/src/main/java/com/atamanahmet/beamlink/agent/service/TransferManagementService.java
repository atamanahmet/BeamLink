package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferManagementService {

    private final TransferSenderService transferSenderService;
    private final BatchSenderService batchSenderService;
    private final DirectorySenderService directorySenderService;
    private final BatchTransferRepository batchTransferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final FileTransferRepository fileTransferRepository;
    private final TransferAsyncSender asyncSender;
    private final AgentService agentService;

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

    /** Stops queue loop, stops active child at next chunk boundary. */
    @Transactional
    public void cancelBatch(UUID id) {
        BatchTransfer bt = batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        bt.setStatus(GroupTransferStatus.CANCELLED);
        batchTransferRepository.save(bt);

        fileTransferRepository.findByBatchTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.CANCELLED);
                    fileTransferRepository.save(ft);
                });
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

    /** Stops queue loop, stops active child at next chunk boundary. */
    @Transactional
    public void cancelDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        dt.setStatus(GroupTransferStatus.CANCELLED);
        directoryTransferRepository.save(dt);

        fileTransferRepository.findByDirectoryTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.CANCELLED);
                    fileTransferRepository.save(ft);
                });
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
}