package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.BatchTransfer;
import com.atamanahmet.beamlink.nexus.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.nexus.domain.enums.PauseOwner;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.InsufficientDiskException;
import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.exception.TransferProtocolException;
import com.atamanahmet.beamlink.nexus.http.TransferHttpClient;
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
    private final BatchTransferService batchTransferService;
    private final DirectoryTransferService directoryTransferService;
    private final FileTransferService fileTransferService;
    private final TransferSender asyncSender;
    private final TransferHttpClient transferHttpClient;
    private final NexusResumePolicyStore resumePolicyStore;
    private final TransferSideResolver sideResolver;
    private final TransferSpeedLimiter transferSpeedLimiter;
    private final ChunkReceiverService chunkReceiverService;
    private final ReceiveStallTracker receiveStallTracker;
    private final StorageService storageService;
    private final DiskReserveProvider diskReserveProvider;

    public void pauseSingle(UUID id) {
        FileTransfer ft = fileTransferService.findByTransferId(id)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null));
        pauseFileTransfer(ft);
    }

    public void resumeSingle(UUID id) {
        FileTransfer ft = fileTransferService.findByTransferId(id)
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
        FileTransfer ft = fileTransferService.findByTransferId(id)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + id, null));
        ft.setSpeedLimitMbps(limitMbps);
        fileTransferService.save(ft);
        transferSpeedLimiter.clearTransfer(id);
    }

    public void cancelSingle(UUID id) {
        transferSenderService.cancel(id);
        fileTransferService.findByTransferId(id).ifPresent(this::notifyPeerCancel);
    }

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void cancelBatch(UUID id) {
        BatchTransfer bt = batchTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        fileTransferService.findByBatchTransferId(id)
                .forEach(ft -> {
                    if (ft.getStatus() == TransferStatus.ACTIVE
                            || ft.getStatus() == TransferStatus.PAUSED) {
                        ft.setStatus(TransferStatus.CANCELLED);
                        ft.setPausedBy(null);
                        fileTransferService.save(ft);
                    }
                    notifyPeerCancel(ft);
                });

        List<FileTransfer> children = fileTransferService.findByBatchTransferId(id);
        bt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        batchTransferService.save(bt);
    }

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void cancelDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        fileTransferService.findByDirectoryTransferId(id)
                .forEach(ft -> {
                    if (ft.getStatus() == TransferStatus.ACTIVE
                            || ft.getStatus() == TransferStatus.PAUSED) {
                        ft.setStatus(TransferStatus.CANCELLED);
                        ft.setPausedBy(null);
                        fileTransferService.save(ft);
                    }
                    notifyPeerCancel(ft);
                });

        List<FileTransfer> children = fileTransferService.findByDirectoryTransferId(id);
        dt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        directoryTransferService.save(dt);
    }

    public void deleteSingle(UUID id) {
        transferSenderService.delete(id);
    }

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void pauseBatch(UUID id) {
        BatchTransfer bt = batchTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        if (bt.getStatus() == GroupTransferStatus.PAUSED) {
            throw alreadyPaused();
        }
        if (bt.getStatus() != GroupTransferStatus.ACTIVE) {
            throw new FileTransferException("Batch is not active: " + bt.getStatus(), null);
        }

        PauseOwner side = sideResolver.resolve(
                bt.getSourceDeviceId(), bt.getTargetDeviceId(), bt.getTargetIp());
        if (side == null) {
            throw new IllegalArgumentException("pausedBy required");
        }
        bt.setPausedBy(side);

        fileTransferService.findByBatchTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.PAUSED);
                    ft.setPausedBy(side);
                    fileTransferService.save(ft);
                    chunkReceiverService.onReceivePaused(ft.getTransferId());
                    notifyPeerPause(ft, side);
                });

        List<FileTransfer> children = fileTransferService.findByBatchTransferId(id);
        bt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        batchTransferService.save(bt);
    }

    public void resumeBatch(UUID id) {
        BatchTransfer bt = batchTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));
        if (bt.getStatus() != GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Batch is not paused: " + bt.getStatus(), null);
        }
        PauseOwner side = sideResolver.resolve(
                bt.getSourceDeviceId(), bt.getTargetDeviceId(), bt.getTargetIp());
        if (!ResumePolicy.canResume(resumePolicyStore.getMode(), side, bt.getPausedBy())) {
            throw resumeForbidden();
        }
        bt.setPausedBy(null);
        batchTransferService.save(bt);
        fileTransferService.findByBatchTransferIdAndStatus(id, TransferStatus.PAUSED)
                .forEach(ft -> {
                    ft.setPausedBy(null);
                    fileTransferService.save(ft);
                    notifyPeerResume(ft);
                });
        batchSenderService.resume(id);
    }

    @Transactional
    public void deleteBatch(UUID id) {
        BatchTransfer bt = batchTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Batch not found: " + id, null));

        GroupTransferStatus s = bt.getStatus();
        if (s == GroupTransferStatus.ACTIVE || s == GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Cannot delete an in-progress batch", null);
        }

        batchTransferService.delete(bt);
    }

    /**
     * Stops queue loop, stops active child at next chunk boundary.
     */
    @Transactional
    public void pauseDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        if (dt.getStatus() == GroupTransferStatus.PAUSED) {
            throw alreadyPaused();
        }
        if (dt.getStatus() != GroupTransferStatus.ACTIVE) {
            throw new FileTransferException("Directory transfer is not active: " + dt.getStatus(), null);
        }

        PauseOwner side = sideResolver.resolve(
                dt.getSourceDeviceId(), dt.getTargetDeviceId(), dt.getTargetIp());
        if (side == null) {
            throw new IllegalArgumentException("pausedBy required");
        }
        dt.setPausedBy(side);

        fileTransferService.findByDirectoryTransferIdAndStatus(id, TransferStatus.ACTIVE)
                .forEach(ft -> {
                    ft.setStatus(TransferStatus.PAUSED);
                    ft.setPausedBy(side);
                    fileTransferService.save(ft);
                    chunkReceiverService.onReceivePaused(ft.getTransferId());
                    notifyPeerPause(ft, side);
                });

        List<FileTransfer> children = fileTransferService.findByDirectoryTransferId(id);
        dt.setStatus(GroupStatusRollup.fromChildren(
                children.stream().map(FileTransfer::getStatus).toList()));
        directoryTransferService.save(dt);
    }

    public void resumeDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));
        if (dt.getStatus() != GroupTransferStatus.PAUSED) {
            throw new FileTransferException(
                    "Directory transfer is not paused: " + dt.getStatus(), null);
        }
        PauseOwner side = sideResolver.resolve(
                dt.getSourceDeviceId(), dt.getTargetDeviceId(), dt.getTargetIp());
        if (!ResumePolicy.canResume(resumePolicyStore.getMode(), side, dt.getPausedBy())) {
            throw resumeForbidden();
        }
        dt.setPausedBy(null);
        directoryTransferService.save(dt);
        fileTransferService.findByDirectoryTransferIdAndStatus(id, TransferStatus.PAUSED)
                .forEach(ft -> {
                    ft.setPausedBy(null);
                    fileTransferService.save(ft);
                    notifyPeerResume(ft);
                });
        directorySenderService.resume(id);
    }

    @Transactional
    public void deleteDirectory(UUID id) {
        DirectoryTransfer dt = directoryTransferService.findById(id)
                .orElseThrow(() -> new FileTransferException("Directory transfer not found: " + id, null));

        GroupTransferStatus s = dt.getStatus();
        if (s == GroupTransferStatus.ACTIVE || s == GroupTransferStatus.PAUSED) {
            throw new FileTransferException("Cannot delete an in-progress directory transfer", null);
        }

        directoryTransferService.delete(dt);
    }

    /**
     * Applies pause from peer notify without echoing another notify.
     */
    public void applyPauseNotify(UUID transferId, PauseOwner pausedBy) {
        applyPauseNotify(transferId, pausedBy, null);
    }

    /**
     * Applies pause from peer notify without echoing another notify.
     */
    public void applyPauseNotify(UUID transferId, PauseOwner pausedBy, String reason) {
        if (pausedBy == null) {
            throw new InvalidRequestException("pausedBy required");
        }
        fileTransferService.findByTransferId(transferId).ifPresent(ft -> {
            ft.setStatus(TransferStatus.PAUSED);
            ft.setPausedBy(PausedByResolver.resolvePausedBy(ft.getPausedBy(), pausedBy));
            if (reason != null && !reason.isBlank()) {
                ft.setFailureReason(reason);
            }
            fileTransferService.save(ft);
            chunkReceiverService.onReceivePaused(transferId);
        });
    }

    /**
     * Applies resume from peer notify and restarts send when this node is source.
     */
    public void applyResumeNotify(UUID transferId) {
        fileTransferService.findByTransferId(transferId).ifPresent(ft -> {
            ft.setPausedBy(null);
            ft.setStatus(TransferStatus.ACTIVE);
            fileTransferService.save(ft);
            PauseOwner side = sideResolver.resolve(
                    ft.getSourceDeviceId(), ft.getTargetDeviceId(), ft.getTargetIp());
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
                ft.getSourceDeviceId(), ft.getTargetDeviceId(), ft.getTargetIp());
        if (side == null) {
            throw new IllegalArgumentException("pausedBy required");
        }
        ft.setStatus(TransferStatus.PAUSED);
        ft.setPausedBy(side);
        fileTransferService.save(ft);
        chunkReceiverService.onReceivePaused(ft.getTransferId());
        notifyPeerPause(ft, side);
    }

    private void resumeFileTransfer(FileTransfer ft) {
        if (ft.getStatus() != TransferStatus.PAUSED) {
            throw new FileTransferException(
                    "Transfer is not paused, current status: " + ft.getStatus(), null);
        }
        PauseOwner side = sideResolver.resolve(
                ft.getSourceDeviceId(), ft.getTargetDeviceId(), ft.getTargetIp());
        if (!ResumePolicy.canResume(resumePolicyStore.getMode(), side, ft.getPausedBy())) {
            throw resumeForbidden();
        }
        if (side == PauseOwner.TARGET) {
            if (DiskReserveRule.CODE.equals(ft.getFailureReason())) {
                long remaining = ft.getFileSize() - ft.getConfirmedOffset();
                if (remaining < 0L) {
                    remaining = 0L;
                }
                if (!DiskReserveRule.allowed(
                        storageService.usableBytes(),
                        remaining,
                        diskReserveProvider.getBytes())) {
                    throw new InsufficientDiskException();
                }
            }
            requestPeerResume(ft);
            ft.setPausedBy(null);
            ft.setFailureReason(null);
            ft.setStatus(TransferStatus.ACTIVE);
            fileTransferService.save(ft);
            receiveStallTracker.touch(ft.getTransferId());
            return;
        }
        ft.setPausedBy(null);
        fileTransferService.save(ft);
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
                peer.ip(), peer.port(), ft.getTransferId(), pausedBy, ft.getFailureReason());
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
