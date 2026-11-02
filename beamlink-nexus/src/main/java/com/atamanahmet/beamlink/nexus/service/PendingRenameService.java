package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.repository.PendingRenameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PendingRenameService {

    private final PendingRenameRepository pendingRenameRepository;

    /**
     * Deletes every pending rename row.
     */
    @Transactional
    public void deleteAll() {
        pendingRenameRepository.deleteAll();
    }
}
