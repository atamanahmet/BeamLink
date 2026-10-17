package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupTransferAsyncSender {

    private final TransferSender transferSender;

    /**
     * Sequential send loop for any group transfer type.
     * Paused files go first, then active/pending in order.
     * Blocks per file so bandwidth is not split.
     */
    @Async
    public void sendAsync(GroupTransferContext ctx) {
        UUID groupId = ctx.groupId();
        GroupTransferService groupService = ctx.groupTransferService();

        List<FileTransfer> queue = groupService.getOrderedQueue(groupId);

        for (FileTransfer ft : queue) {
            GroupTransferStatus currentStatus = groupService.getStatus(groupId);

            if (currentStatus == GroupTransferStatus.CANCELLED
                    || currentStatus == GroupTransferStatus.FAILED) {
                log.info("Group transfer {} stopped before file {}: status={}",
                        groupId, ft.getFileName(), currentStatus);
                return;
            }

            if (currentStatus == GroupTransferStatus.PAUSED) {
                log.info("Group transfer {} paused before file: {}",
                        groupId, ft.getFileName());
                return;
            }

            try {
                transferSender.sendBlocking(
                        ft.getTransferId(), ctx.targetIp(), ctx.targetPort());
            } catch (RuntimeException e) {
                log.error("File failed in group {}: {}", groupId, ft.getFileName(), e);
            }
        }

        resolveGroupOutcome(groupId, groupService);
    }

    private void resolveGroupOutcome(UUID groupId, GroupTransferService groupService) {
        groupService.applyChildStatusRollup(groupId);
        GroupTransferStatus status = groupService.getStatus(groupId);
        log.info("Group transfer {} resolved to {}", groupId, status);
    }
}