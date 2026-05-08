package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.BatchTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchTransferRepository extends JpaRepository<BatchTransfer, UUID> {
    Optional<BatchTransfer> findByBatchTransferId(UUID batchTransferId);
}
