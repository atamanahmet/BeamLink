package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.dto.DeviceDTO;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.dto.PeerListResponse;
import com.atamanahmet.beamlink.nexus.dto.PeerSummaryResponse;
import com.atamanahmet.beamlink.nexus.mapper.DeviceMapper;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.service.DeviceService;
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
        private final DeviceService deviceService;
        private final DeviceMapper deviceMapper;
        private final PeerMapper peerMapper;

        /**
         * Online peers for devices. PeerDTO only.
         */
        @GetMapping("/online")
        public ResponseEntity<List<PeerDTO>> getOnlinePeers(
                        @RequestParam(required = false) UUID excludeDeviceId) {
                List<PeerDTO> peers = deviceService.getOnlineDevices().stream()
                                .filter(a -> excludeDeviceId == null || !a.getId().equals(excludeDeviceId))
                                .map(peerMapper::fromDevice)
                                .toList();
                return ResponseEntity.ok(peers);
        }

        /**
         * Slim peer list for nexus UI. No tokens or sync fields.
         */
        @GetMapping("/summary")
        public ResponseEntity<List<PeerSummaryResponse>> getPeerSummaries() {
                List<PeerSummaryResponse> peers = new ArrayList<>();
                peers.add(peerMapper.nexusPeerSummary());
                deviceService.getDevicesByState(DeviceState.APPROVED).stream()
                        .map(PeerMapper::toSummary)
                        .forEach(peers::add);
                return ResponseEntity.ok(peers);
        }

        @GetMapping
        public ResponseEntity<PeerListResponse> getAllPeers(@RequestParam(required = false) UUID excludeDeviceId) {
                List<Device> devices = deviceService.getDevicesByState(DeviceState.APPROVED).stream()
                        .filter(a -> !a.getId().equals(excludeDeviceId))
                        .toList();

                List<PeerDTO> peers = new ArrayList<>();
                peers.add(peerMapper.nexusPeer());
                devices.stream()
                        .map(peerMapper::fromDevice)
                        .forEach(peers::add);

                return ResponseEntity.ok(
                        PeerListResponse.builder()
                                .peers(peers)
                                .version(peerListService.getCurrentVersion())
                                .build()
                );
        }

        @GetMapping("/{deviceId}/address")
        public ResponseEntity<Map<String, Object>> getDeviceAddress(@PathVariable UUID deviceId) {
                DeviceDTO device = deviceMapper.toDTO(deviceService.findByDeviceId(deviceId));
                return ResponseEntity.ok(Map.of("ip", device.getIpAddress(), "port", device.getPort()));
        }

        @GetMapping("/version")
        public ResponseEntity<Map<String, Long>> getPeerListVersion() {
                return ResponseEntity.ok(Map.of("version", peerListService.getCurrentVersion()));
        }
}