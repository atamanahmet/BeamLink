package com.atamanahmet.beamlink.peer.service;

import com.atamanahmet.beamlink.peer.config.NexusAddressHolder;
import com.atamanahmet.beamlink.peer.domain.Peer;
import com.atamanahmet.beamlink.peer.security.NexusTlsPin;
import com.atamanahmet.beamlink.peer.dto.PeerDTO;
import com.atamanahmet.beamlink.peer.dto.PeerListResponse;
import com.atamanahmet.beamlink.peer.dto.PeerStatusUpdate;
import com.atamanahmet.beamlink.peer.event.DeviceIdentityChangedEvent;
import com.atamanahmet.beamlink.peer.mapper.PeerMapper;
import com.atamanahmet.beamlink.peer.repository.PeerRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeerCacheService {

    private final Logger log = LoggerFactory.getLogger(PeerCacheService.class);

    private final WebClient nexusWebClient;
    private final PeerRepository peerRepository;
    private final NexusAddressHolder nexusAddressHolder;
    private final ApplicationEventPublisher eventPublisher;
    private final NexusTlsPin nexusTlsPin;

    private volatile boolean initialPeersReceived = false;
    private volatile String lastNexusAuthHint;

    @Getter
    private long currentPeerListVersion = 0L;

    /** In-memory cache, always in sync with DB after any write. */
    private List<Peer> cachedPeers = new ArrayList<>();

    public List<Peer> getAllPeers(UUID deviceId, String publicToken) {
        if (cachedPeers.isEmpty()) refreshPeersFromNexus(deviceId, publicToken);
        if (cachedPeers.isEmpty()) loadFromDb();
        return new ArrayList<>(cachedPeers);
    }

    public List<Peer> getOnlinePeers(UUID deviceId, String publicToken) {
        return getAllPeers(deviceId, publicToken).stream()
                .filter(Peer::isOnline)
                .toList();
    }

    public void refreshPeersFromNexus(UUID deviceId, String publicToken) {
        if (initialPeersReceived) {
            log.debug("WS peer list already active. Skipping HTTP refresh.");
            return;
        }

        if (deviceId == null) {
            log.info("No device ID yet. Skipping peer refresh.");
            loadFromDb();
            return;
        }

        if (publicToken == null || publicToken.isBlank()) {
            log.warn("No public token available. Skipping peer refresh.");
            loadFromDb();
            return;
        }

        if (!nexusAddressHolder.isResolved() || !nexusTlsPin.hasPin()) {
            log.warn("Nexus URL or TLS pin not ready. Skipping peer refresh.");
            loadFromDb();
            return;
        }

        try {
            PeerListResponse response = nexusWebClient.get()
                    .uri(nexusAddressHolder.getNexusUrl() + "/api/nexus/peers?excludeDeviceId=" + deviceId)
                    .header("X-Auth-Token", publicToken)
                    .retrieve()
                    .bodyToMono(PeerListResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        int status = ex.getStatusCode().value();
                        if (status == 401 || status == 403) {
                            log.warn("Peer fetch rejected [{}], token may not be active yet.", status);
                            markTokenRejected(status);
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
            setAuthHint(null);

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

    public void updatePeerStatuses(List<PeerStatusUpdate> deviceStatuses) {
        if (deviceStatuses == null || deviceStatuses.isEmpty()) return;
        for (PeerStatusUpdate status : deviceStatuses) {
            cachedPeers.stream()
                    .filter(p -> p.getDeviceId().toString().equals(status.getDeviceId()))
                    .findFirst()
                    .ifPresent(p -> p.setOnline(status.isOnline()));
        }
    }

    public void addOrUpdatePeer(PeerDTO incoming) {
        Peer peer = PeerMapper.toEntity(incoming);
        boolean found = false;
        for (int i = 0; i < cachedPeers.size(); i++) {
            if (cachedPeers.get(i).getDeviceId().equals(peer.getDeviceId())) {
                cachedPeers.set(i, peer);
                found = true;
                break;
            }
        }
        if (!found) cachedPeers.add(peer);
        saveToDb();
        log.info("Peer added/updated: {} ({})", peer.getDeviceName(), peer.getDeviceId());
    }

    public void markOffline(UUID deviceId) {
        cachedPeers.stream()
                .filter(p -> p.getDeviceId().equals(deviceId))
                .findFirst()
                .ifPresent(p -> {
                    p.setOnline(false);
                    saveToDb();
                    log.info("Peer {} marked offline", deviceId);
                });
    }

    /**
     * Cached peer by LAN ip and advertised http or tls port.
     */
    public Peer findByIpAndPort(String ip, int port) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        for (Peer peer : cachedPeers) {
            if (!ip.equals(peer.getIpAddress())) {
                continue;
            }
            if (peer.getPort() == port) {
                return peer;
            }
            if (peer.getTlsPort() != null && peer.getTlsPort() == port) {
                return peer;
            }
        }
        return null;
    }

    public void clearCache() {
        cachedPeers = new ArrayList<>();
        currentPeerListVersion = 0L;
        initialPeersReceived = false;
        peerRepository.deleteAll();
        setAuthHint(null);
        log.info("Peer cache cleared.");
    }

    /**
     * Last 401/403 from nexus for this device token, or null.
     */
    public String getLastNexusAuthHint() {
        return lastNexusAuthHint;
    }

    /**
     * Records a 401/403 on this device token for the UI hint.
     */
    public void markTokenRejected(int status) {
        setAuthHint("Nexus rejected this device token (" + status + "). Re-register.");
    }

    /**
     * Stores the hint and notifies the UI only when the text changes.
     */
    private void setAuthHint(String hint) {
        String previous = lastNexusAuthHint;
        lastNexusAuthHint = hint;
        if (!Objects.equals(previous, hint)) {
            eventPublisher.publishEvent(new DeviceIdentityChangedEvent(this));
        }
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