package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.PeerListVersion;
import com.atamanahmet.beamlink.nexus.repository.PeerListVersionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PeerListService {
    private static final int PEER_LIST_VERSION_ID = 1;
    private final PeerListVersionRepository peerListVersionRepository;
    private volatile long cachedVersion;

    public void initialize() {
        PeerListVersion peerListVersion = this.peerListVersionRepository.findById(1).orElseGet(this::createInitialVersion);
        this.cachedVersion = peerListVersion.getVersion();
    }

    @Transactional
    public long incrementVersion() {
        PeerListVersion peerListVersion = this.peerListVersionRepository.findById(1).orElseThrow(() -> new IllegalStateException("Peer list version missing"));
        peerListVersion.setVersion(peerListVersion.getVersion() + 1L);
        this.cachedVersion = peerListVersion.getVersion();
        return this.cachedVersion;
    }

    public long getCurrentVersion() {
        return this.cachedVersion;
    }

    private PeerListVersion createInitialVersion() {
        PeerListVersion peerListVersion = new PeerListVersion();
        peerListVersion.setId(1);
        peerListVersion.setVersion(1L);
        return this.peerListVersionRepository.save(peerListVersion);
    }
}
