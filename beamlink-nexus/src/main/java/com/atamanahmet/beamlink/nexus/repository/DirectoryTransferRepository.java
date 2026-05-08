package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.DirectoryTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DirectoryTransferRepository extends JpaRepository<DirectoryTransfer, UUID> {
    Optional<DirectoryTransfer> findByDirectoryTransferId(UUID directoryTransferId);
}
