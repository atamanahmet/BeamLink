package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.dto.PeerListResponse;
import com.atamanahmet.beamlink.nexus.mapper.AgentMapper;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.service.PeerListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/peers")
@RequiredArgsConstructor
public class PeerController {

        private final PeerListService peerListService;
        private final AgentService agentService;
        private final AgentMapper agentMapper;
        private final PeerMapper peerMapper;

        @GetMapping("/online")
        public ResponseEntity<List<AgentDTO>> getOnlinePeers(@RequestParam(required = false) UUID excludeAgentId) {
                return ResponseEntity.ok(agentService.getOnlineAgentsExcluding(excludeAgentId));
        }

        @GetMapping
        public ResponseEntity<PeerListResponse> getAllPeers(@RequestParam(required = false) UUID excludeAgentId) {
                List<Agent> agents = agentService.getAgentsByState(AgentState.APPROVED).stream()
                        .filter(a -> !a.getId().equals(excludeAgentId))
                        .toList();

                List<PeerDTO> peers = new ArrayList<>();
                peers.add(peerMapper.nexusPeer());
                agents.stream()
                        .map(peerMapper::fromAgent)
                        .forEach(peers::add);

                return ResponseEntity.ok(
                        PeerListResponse.builder()
                                .peers(peers)
                                .version(peerListService.getCurrentVersion())
                                .build()
                );
        }

        @GetMapping("/{agentId}/address")
        public ResponseEntity<Map<String, Object>> getAgentAddress(@PathVariable UUID agentId) {
                AgentDTO agent = agentMapper.toDTO(agentService.findByAgentId(agentId));
                return ResponseEntity.ok(Map.of("ip", agent.getIpAddress(), "port", agent.getPort()));
        }

        @GetMapping("/version")
        public ResponseEntity<Map<String, Long>> getPeerListVersion() {
                return ResponseEntity.ok(Map.of("version", peerListService.getCurrentVersion()));
        }
}