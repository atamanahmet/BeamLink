package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;

import java.util.List;
import java.util.UUID;

public interface GroupTransferService {

    UUID getGroupId();

    GroupTransferStatus getStatus(UUID groupId);

    List<FileTransfer> getOrderedQueue(UUID groupId);

    List<FileTransfer> getAllChildren(UUID groupId);

    void markActive(UUID groupId);

    void markCompleted(UUID groupId);

    void markPartial(UUID groupId);

    void markFailed(UUID groupId);

    void markCancelled(UUID groupId);
}