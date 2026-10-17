package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.exception.TransferProtocolException;
import com.atamanahmet.beamlink.agent.http.TransferHttpClient;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final AgentResumePolicyHolder resumePolicyHolder;
    private final TransferSideResolver sideResolver;
    private final TransferSpeedLimiter transferSpeedLimiter;
    private final ChunkReceiverService chunkReceiverService;
    private final ReceiveStallTracker receiveStallTracker;

    public void pauseSingle(UUID id) {
        FileTransfer ft = fileTransferRepository.findByTransferId(id)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null));
        pauseFileTransfer(ft);
    }

    public void resumeSingle(UUID id) {
        FileTransfer ft = fileTransferRepository.findByTransferId(id)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null));
        resumeFileTransfer(ft);
    }

    /**
     * Sets or clears the per-file speed override. Null clears.
     */
    @Transactional
    public void setSingleSpeedLimit(UUID id, Double limitMbps) {
        if (limitMbps != null && limitMbps <= 0) {
            throw new TransferProtocolException(
                    "INVALID_SPEED_LIMIT",
                    "Speed limit must be null or a positive Mb/s value",
                    HttpStatus.BAD_REQUEST);
        }
        FileTransfer ft = fileTransferRepository.findByTransferId(id)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null));
        ft.setSpeedLimitMbps(limitMbps);
        fileTransferRepository.save(ft);
        transferSpeedLimiter.clearTransfer(id);
    }

    public void cancelSingle(UUID id) {
        transferSenderService.cancel(id);
        fileTransferRepository.findByTransferId(id).ifPresent(this::notifyPeerCancel);
    }

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void cancelBatch(UUID id) {
        BatchTransfer bt = batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        fileTransferRepository.findByBatchTransferId(id)
                .forEach(ft -> {
                    if (ft.getStatus() == TransferStatus.ACTIVE
                            || ft.getStatus() == TransferStatus.PAUSED) {
                        ft.setStatus(TransferStatus.CANCELLED);
                        ft.setPausedBy(null);
                        fileTransferRepository.save(ft);
                    }
                    notifyPeerCancel(ft);
                });

        List<FileTransfer> children = fileTransferRepository.findByBatchTransferId(id);
        bt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        batchTransferRepository.save(bt);
    }

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void cancelDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        fileTransferRepository.findByDirectoryTransferId(id)
                .forEach(ft -> {
                    if (ft.getStatus() == TransferStatus.ACTIVE
                            || ft.getStatus() == TransferStatus.PAUSED) {
                        ft.setStatus(TransferStatus.CANCELLED);
                        ft.setPausedBy(null);
                        fileTransferRepository.save(ft);
                    }
                    notifyPeerCancel(ft);
                });

        List<FileTransfer> children = fileTransferRepository.findByDirectoryTransferId(id);
        dt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        directoryTransferRepository.save(dt);
    }

    public void deleteSingle(UUID id) {
        transferSenderService.delete(id);
    }

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void pauseBatch(UUID id) {
        BatchTransfer bt = batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        if (bt.getStatus() == GroupTransferStatus.PAUSED) {
            throw alreadyPaused();
        }
        if (bt.getStatus() != GroupTransferStatus.ACTIVE) {
            throw new FileTransferException("Batch is not active: " + bt.getStatus(), null);
        }

        PauseOwner side = sideResolver.resolve(
                bt.getSourceAgentId(), bt.getTargetAgentId(), bt.getTargetIp());
        bt.setPausedBy(side);

        fileTransferRepository.findByBatchTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.PAUSED);
                    ft.setPausedBy(side);
                    fileTransferRepository.save(ft);
                    chunkReceiverService.onReceivePaused(ft.getTransferId());
                    notifyPeerPause(ft, side);
                });

        List<FileTransfer> children = fileTransferRepository.findByBatchTransferId(id);
        bt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        batchTransferRepository.save(bt);
    }

    public void resumeBatch(UUID id) {
        BatchTransfer bt = batchTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));
        if (bt.getStatus() != GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Batch is not paused: " + bt.getStatus(), null);
        }
        PauseOwner side = sideResolver.resolve(
                bt.getSourceAgentId(), bt.getTargetAgentId(), bt.getTargetIp());
        if (!ResumePolicy.canResume(resumePolicyHolder.getMode(), side, bt.getPausedBy())) {
            throw resumeForbidden();
        }
        bt.setPausedBy(null);
        batchTransferRepository.save(bt);
        fileTransferRepository.findByBatchTransferIdAndStatus(id, TransferStatus.PAUSED)
                .forEach(ft -> {
                    ft.setPausedBy(null);
                    fileTransferRepository.save(ft);
                    notifyPeerResume(ft);
                });
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

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void pauseDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        if (dt.getStatus() == GroupTransferStatus.PAUSED) {
            throw alreadyPaused();
        }
        if (dt.getStatus() != GroupTransferStatus.ACTIVE) {
            throw new FileTransferException("Directory transfer is not active: " + dt.getStatus(), null);
        }

        PauseOwner side = sideResolver.resolve(
                dt.getSourceAgentId(), dt.getTargetAgentId(), dt.getTargetIp());
        dt.setPausedBy(side);

        fileTransferRepository.findByDirectoryTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.PAUSED);
                    ft.setPausedBy(side);
                    fileTransferRepository.save(ft);
                    chunkReceiverService.onReceivePaused(ft.getTransferId());
                    notifyPeerPause(ft, side);
                });

        List<FileTransfer> children = fileTransferRepository.findByDirectoryTransferId(id);
        dt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        directoryTransferRepository.save(dt);
    }

    public void resumeDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferRepository.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));
        if (dt.getStatus() != GroupTransferStatus.PAUSED) {
            throw new FileTransferException(
                    "Directory transfer is not paused: " + dt.getStatus(), null);
        }
        PauseOwner side = sideResolver.resolve(
                dt.getSourceAgentId(), dt.getTargetAgentId(), dt.getTargetIp());
        if (!ResumePolicy.canResume(resumePolicyHolder.getMode(), side, dt.getPausedBy())) {
            throw resumeForbidden();
        }
        dt.setPausedBy(null);
        directoryTransferRepository.save(dt);
        fileTransferRepository.findByDirectoryTransferIdAndStatus(id, TransferStatus.PAUSED)
                .forEach(ft -> {
                    ft.setPausedBy(null);
                    fileTransferRepository.save(ft);
                    notifyPeerResume(ft);
                });
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
     * Applies pause from peer notify without echoing another notify.
     */
    public void applyPauseNotify(UUID transferId, PauseOwner pausedBy) {
        fileTransferRepository.findByTransferId(transferId).ifPresent(ft -> {
            ft.setStatus(TransferStatus.PAUSED);
            ft.setPausedBy(PausedByResolver.resolvePausedBy(ft.getPausedBy(), pausedBy));
            fileTransferRepository.save(ft);
            chunkReceiverService.onReceivePaused(transferId);
        });
    }

    /**
     * Applies resume from peer notify and restarts send when this node is source.
     */
    public void applyResumeNotify(UUID transferId) {
        fileTransferRepository.findByTransferId(transferId).ifPresent(ft -> {
            ft.setPausedBy(null);
            ft.setStatus(TransferStatus.ACTIVE);
            fileTransferRepository.save(ft);
            PauseOwner side = sideResolver.resolve(
                    ft.getSourceAgentId(), ft.getTargetAgentId(), ft.getTargetIp());
            if (side == PauseOwner.TARGET) {
                receiveStallTracker.touch(ft.getTransferId());
            }
            if (side == PauseOwner.SOURCE
                    && ft.getTargetIp() != null
                    && ft.getTargetPort() != null) {
                asyncSender.sendAsync(ft.getTransferId(), ft.getTargetIp(), ft.getTargetPort());
            }
        });
    }

    private void pauseFileTransfer(FileTransfer ft) {
        if (ft.getStatus() == TransferStatus.PAUSED) {
            throw alreadyPaused();
        }
        if (ft.getStatus() != TransferStatus.ACTIVE) {
            throw new FileTransferException(
                    "Transfer is not active: " + ft.getStatus(), null);
        }
        PauseOwner side = sideResolver.resolve(
                ft.getSourceAgentId(), ft.getTargetAgentId(), ft.getTargetIp());
        ft.setStatus(TransferStatus.PAUSED);
        ft.setPausedBy(side);
        fileTransferRepository.save(ft);
        chunkReceiverService.onReceivePaused(ft.getTransferId());
        notifyPeerPause(ft, side);
    }

    private void resumeFileTransfer(FileTransfer ft) {
        if (ft.getStatus() != TransferStatus.PAUSED) {
            throw new FileTransferException(
                    "Transfer is not paused, current status: " + ft.getStatus(), null);
        }
        PauseOwner side = sideResolver.resolve(
                ft.getSourceAgentId(), ft.getTargetAgentId(), ft.getTargetIp());
        if (!ResumePolicy.canResume(resumePolicyHolder.getMode(), side, ft.getPausedBy())) {
            throw resumeForbidden();
        }
        if (side == PauseOwner.TARGET) {
            requestPeerResume(ft);
            ft.setPausedBy(null);
            ft.setStatus(TransferStatus.ACTIVE);
            fileTransferRepository.save(ft);
            receiveStallTracker.touch(ft.getTransferId());
            return;
        }
        ft.setPausedBy(null);
        fileTransferRepository.save(ft);
        transferSenderService.resume(ft.getTransferId());
        notifyPeerResume(ft);
        if (ft.getTargetIp() != null && ft.getTargetPort() != null) {
            asyncSender.sendAsync(ft.getTransferId(), ft.getTargetIp(), ft.getTargetPort());
        }
    }

    /**
     * Asks the sender to continue. Leaves local row unchanged when the peer fails.
     */
    private void requestPeerResume(FileTransfer ft) {
        PeerAddress peer = peerAddress(ft);
        if (peer == null) {
            throw new TransferProtocolException(
                    "PEER_UNREACHABLE",
                    "No peer address stored for resume",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        transferHttpClient.requestResumeFromPeer(peer.ip(), peer.port(), ft.getTransferId());
    }

    private TransferProtocolException alreadyPaused() {
        return new TransferProtocolException(
                "TRANSFER_ALREADY_PAUSED",
                "Transfer is already paused",
                HttpStatus.CONFLICT);
    }

    private TransferProtocolException resumeForbidden() {
        return new TransferProtocolException(
                "RESUME_FORBIDDEN",
                "Resume is not allowed for this side",
                HttpStatus.FORBIDDEN);
    }

    /**
     * Notifies peer node to pause their side. Http client already swallows failures.
     */
    private void notifyPeerPause(FileTransfer ft, PauseOwner pausedBy) {
        PeerAddress peer = peerAddress(ft);
        if (peer == null) {
            log.warn("No peer address stored for transfer {}, skipping pause notify",
                    ft.getTransferId());
            return;
        }
        transferHttpClient.notifyPauseToPeer(
                peer.ip(), peer.port(), ft.getTransferId(), pausedBy);
    }

    /**
     * Notifies peer node to resume their side. Http client already swallows failures.
     */
    private void notifyPeerResume(FileTransfer ft) {
        PeerAddress peer = peerAddress(ft);
        if (peer == null) {
            log.warn("No peer address stored for transfer {}, skipping resume notify",
                    ft.getTransferId());
            return;
        }
        transferHttpClient.notifyResumeToPeer(peer.ip(), peer.port(), ft.getTransferId());
    }

    /**
     * Notifies peer node to cancel their side. Fire-and-forget, local cancel already done.
     */
    private void notifyPeerCancel(FileTransfer ft) {
        PeerAddress peer = peerAddress(ft);
        if (peer == null) {
            log.warn("No peer address stored for transfer {}, skipping cancel notify",
                    ft.getTransferId());
            return;
        }
        transferHttpClient.notifyCancelToPeer(peer.ip(), peer.port(), ft.getTransferId());
    }

    private PeerAddress peerAddress(FileTransfer ft) {
        if (ft.getTargetIp() != null && !ft.getTargetIp().isBlank()
                && ft.getTargetPort() != null) {
            return new PeerAddress(ft.getTargetIp(), ft.getTargetPort());
        }
        if (ft.getSourceIp() != null && !ft.getSourceIp().isBlank()
                && ft.getSourcePort() != null) {
            return new PeerAddress(ft.getSourceIp(), ft.getSourcePort());
        }
        return null;
    }

    private record PeerAddress(String ip, int port) {}
}
