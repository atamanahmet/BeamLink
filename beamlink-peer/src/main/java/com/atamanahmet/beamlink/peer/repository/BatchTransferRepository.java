package com.atamanahmet.beamlink.peer.repository;

import com.atamanahmet.beamlink.peer.domain.BatchTransfer;
import com.atamanahmet.beamlink.peer.domain.enums.GroupTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchTransferRepository extends JpaRepository<BatchTransfer, UUID> {
    Optional<BatchTransfer> findByBatchTransferId(UUID batchTransferId);
    List<BatchTransfer> findAllByOrderByCreatedAtDesc();
    boolean existsByStatus(GroupTransferStatus status);

    long countByStatus(GroupTransferStatus status);
}
