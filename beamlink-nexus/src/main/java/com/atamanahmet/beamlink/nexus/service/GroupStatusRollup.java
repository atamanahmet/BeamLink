package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;

import java.util.Collection;

/**
 * Pure rollup of child file statuses into one group status.
 */
public final class GroupStatusRollup {

    private GroupStatusRollup() {}

    /**
     * Applies in-progress first, then finished-child rules.
     */
    public static GroupTransferStatus fromChildren(Collection<TransferStatus> children) {
        if (children == null || children.isEmpty()) {
            return GroupTransferStatus.CANCELLED;
        }

        boolean anyActive = false;
        boolean anyPaused = false;
        boolean anyPending = false;
        boolean anyCompleted = false;
        boolean anyFailed = false;
        boolean anyCancelled = false;
        int counted = 0;

        for (TransferStatus status : children) {
            if (status == null) {
                continue;
            }
            counted++;
            switch (status) {
                case ACTIVE -> anyActive = true;
                case PAUSED -> anyPaused = true;
                case PENDING -> anyPending = true;
                case COMPLETED -> anyCompleted = true;
                case FAILED -> anyFailed = true;
                case CANCELLED -> anyCancelled = true;
                case EXPIRED -> {
                    // finished, no dedicated group rule
                }
            }
        }

        if (counted == 0) {
            return GroupTransferStatus.CANCELLED;
        }
        if (anyActive) {
            return GroupTransferStatus.ACTIVE;
        }
        if (anyPaused) {
            return GroupTransferStatus.PAUSED;
        }
        if (anyPending) {
            return GroupTransferStatus.PENDING;
        }
        if (allCompleted(children)) {
            return GroupTransferStatus.COMPLETED;
        }
        if (anyCompleted && (anyFailed || anyCancelled)) {
            return GroupTransferStatus.PARTIAL;
        }
        if (!anyCompleted && anyFailed) {
            return GroupTransferStatus.FAILED;
        }
        return GroupTransferStatus.CANCELLED;
    }

    private static boolean allCompleted(Collection<TransferStatus> children) {
        boolean saw = false;
        for (TransferStatus status : children) {
            if (status == null) {
                continue;
            }
            if (status != TransferStatus.COMPLETED) {
                return false;
            }
            saw = true;
        }
        return saw;
    }
}
