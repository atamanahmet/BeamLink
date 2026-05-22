package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.Peer;
import com.atamanahmet.beamlink.agent.dto.PeerDTO;
import com.atamanahmet.beamlink.agent.dto.PeerListResponse;
import com.atamanahmet.beamlink.agent.dto.PeerStatusUpdate;
import com.atamanahmet.beamlink.agent.mapper.PeerMapper;
import com.atamanahmet.beamlink.agent.repository.PeerRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeerCacheService {

    private final Logger log = LoggerFactory.getLogger(PeerCacheService.class);

    private final WebClient nexusWebClient;
    private final PeerRepository peerRepository;

    private volatile boolean initialPeersReceived = false;

    @Getter
    private long currentPeerListVersion = 0L;

    /** In-memory cache, always in sync with DB after any write. */
    private List<Peer> cachedPeers = new ArrayList<>();

    public List<Peer> getAllPeers(UUID agentId, String publicToken) {
        if (cachedPeers.isEmpty()) refreshPeersFromNexus(agentId, publicToken);
        if (cachedPeers.isEmpty()) loadFromDb();
        return new ArrayList<>(cachedPeers);
    }

    public List<Peer> getOnlinePeers(UUID agentId, String publicToken) {
        return getAllPeers(agentId, publicToken).stream()
                .filter(Peer::isOnline)
                .toList();
    }

    public void refreshPeersFromNexus(UUID agentId, String publicToken) {
        if (initialPeersReceived) {
            log.debug("WS peer list already active. Skipping HTTP refresh.");
            return;
        }

        if (agentId == null) {
            log.info("No agent ID yet. Skipping peer refresh.");
            loadFromDb();
            return;
        }

        if (publicToken == null || publicToken.isBlank()) {
            log.warn("No public token available. Skipping peer refresh.");
            loadFromDb();
            return;
        }

        try {
            PeerListResponse response = nexusWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/nexus/peers")
                            .queryParam("excludeAgentId", agentId)
                            .build())
                    .header("X-Auth-Token", publicToken)
                    .retrieve()
                    .bodyToMono(PeerListResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        int status = ex.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            log.warn("Peer fetch rejected [{}], token may not be active yet.", status);
                        } else {
                            log.error("Peer fetch failed [{}]: {}", status, ex.getMessage());
                        }
                        return Mono.empty();
                    })
                    .onErrorResume(e -> {
                        log.error("Peer fetch failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                        return Mono.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            if (response == null) {
                log.debug("No peer response received. Loading from DB.");
                loadFromDb();
                return;
            }

            cachedPeers = response.getPeers() != null
                    ? response.getPeers().stream()
                    .map(PeerMapper::toEntity)
                    .collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>();
            currentPeerListVersion = response.getVersion();
            saveToDb();

            log.info("Refreshed peer list: {} peers (version: {})", cachedPeers.size(), currentPeerListVersion);

        } catch (Exception e) {
            log.error("Could not refresh from nexus: {}", e.getMessage());
            log.info("Using cached peer list: {} peers", cachedPeers.size());
        }
    }

    public void updatePeers(List<PeerDTO> peers, long version) {
        cachedPeers = peers.stream()
                .map(PeerMapper::toEntity)
                .collect(Collectors.toCollection(ArrayList::new));
        currentPeerListVersion = version;
        initialPeersReceived = true;
        saveToDb();
        log.info("Peer list updated via WS: {} peers (version: {})", cachedPeers.size(), currentPeerListVersion);
    }

    public void updatePeerStatuses(List<PeerStatusUpdate> agentStatuses) {
        if (agentStatuses == null || agentStatuses.isEmpty()) return;
        for (PeerStatusUpdate status : agentStatuses) {
            cachedPeers.stream()
                    .filter(p -> p.getAgentId().toString().equals(status.getAgentId()))
                    .findFirst()
                    .ifPresent(p -> p.setOnline(status.isOnline()));
        }
    }

    public void addOrUpdatePeer(PeerDTO incoming) {
        Peer peer = PeerMapper.toEntity(incoming);
        boolean found = false;
        for (int i = 0; i < cachedPeers.size(); i++) {
            if (cachedPeers.get(i).getAgentId().equals(peer.getAgentId())) {
                cachedPeers.set(i, peer);
                found = true;
                break;
            }
        }
        if (!found) cachedPeers.add(peer);
        saveToDb();
        log.info("Peer added/updated: {} ({})", peer.getAgentName(), peer.getAgentId());
    }

    public void markOffline(UUID agentId) {
        cachedPeers.stream()
                .filter(p -> p.getAgentId().equals(agentId))
                .findFirst()
                .ifPresent(p -> {
                    p.setOnline(false);
                    saveToDb();
                    log.info("Peer {} marked offline", agentId);
                });
    }

    public void clearCache() {
        cachedPeers.clear();
        peerRepository.deleteAll();
        log.info("Peer cache cleared.");
    }

    /** Loads peers from DB into in-memory cache on startup or fallback. */
    private void loadFromDb() {
        cachedPeers = new ArrayList<>(peerRepository.findAll());
        log.info("Loaded {} peers from DB.", cachedPeers.size());
    }

    /**
     * Full replacement, peer list is always comes from Nexus.
     */
    private void saveToDb() {
        peerRepository.deleteAll();
        peerRepository.saveAll(cachedPeers);
    }
}