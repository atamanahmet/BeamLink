package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;

public final class SendCompleteRule {

    private SendCompleteRule() {}

    /**
     * True when status is ACTIVE and confirmed bytes equal file size.
     */
    public static boolean shouldMarkComplete(
            TransferStatus status, long confirmedOffset, long fileSize) {
        return status == TransferStatus.ACTIVE
                && FinalSizeRule.matches(confirmedOffset, fileSize);
    }
}
