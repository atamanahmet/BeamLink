package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.event.DeviceApprovedEvent;
import com.atamanahmet.beamlink.nexus.event.DeviceRenamedEvent;
import com.atamanahmet.beamlink.nexus.exception.DeviceNotFoundException;
import com.atamanahmet.beamlink.nexus.exception.InvalidRequestException;
import com.atamanahmet.beamlink.nexus.exception.NameAlreadyInUseException;
import com.atamanahmet.beamlink.nexus.mapper.DeviceMapper;
import com.atamanahmet.beamlink.nexus.mapper.PeerMapper;
import com.atamanahmet.beamlink.nexus.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private static final int OFFLINE_THRESHOLD_MINUTES = 2;

    private static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(2L);

    private final DeviceRepository deviceRepository;
    private final PeerListService peerListService;
    private final DeviceMapper deviceMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PeerMapper peerMapper;

    public Device saveDevice(Device device) {
        return deviceRepository.save(device);
    }

    public Optional<Device> getByIpAddressAndPort(String ipAddress, int port) {
        return deviceRepository.findByIpAddressAndPort(ipAddress, port);
    }

    /**
     * Device by LAN ip and advertised http or tls port.
     */
    public Optional<Device> findByLanAddress(String ipAddress, int port) {
        Optional<Device> byHttp = deviceRepository.findByIpAddressAndPort(ipAddress, port);
        if (byHttp.isPresent()) {
            return byHttp;
        }
        return deviceRepository.findByIpAddressAndTlsPort(ipAddress, port);
    }

    @Transactional
    public DeviceStatusResponse updateDeviceStatus(DeviceStatusRequest request) {
        StatusUpdatePayload payload = StatusUpdatePayload.builder()
                .ipAddress(request.getIpAddress())
                .port(request.getPort())
                .peerVersion(request.getPeerVersion())
                .build();

        boolean addressChanged = applyStatusUpdate(request.getDeviceId(), payload);
        if (addressChanged) {
            peerListService.incrementVersion();
            log.info("Device {} address changed via HTTP fallback, peer list version incremented.", request.getDeviceId());
        }

        Device device = findByDeviceId(request.getDeviceId());
        boolean peerOutdated = request.getPeerVersion() < peerListService.getCurrentVersion();

        List<PeerDTO> peers = null;
        if (peerOutdated && device.getState() == DeviceState.APPROVED) {
            peers = getAllApproved().stream()
                    .map(peerMapper::fromDevice)
                    .collect(Collectors.toList());
            peers.add(0, peerMapper.nexusPeer());
        }

        return DeviceStatusResponse.builder()
                .status(device.getState().name())
                .peerOutdated(peerOutdated)
                .peers(peers)
                .build();
    }

    @Transactional
    public void deleteDevice(UUID deviceId) {
        Device device = findByDeviceId(deviceId);
        deviceRepository.delete(device);
        peerListService.incrementVersion();
        log.info("Device deleted: {}", deviceId);
    }

    /**
     * Deletes every device row.
     */
    @Transactional
    public void deleteAll() {
        deviceRepository.deleteAll();
        log.info("All devices deleted.");
    }

    public Device findByDeviceId(UUID deviceId) {
        return deviceRepository.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("Unknown device: " + deviceId));
    }

    public Optional<Device> findOptional(UUID deviceId) {
        return deviceRepository.findById(deviceId);
    }

    public Optional<Device> findByFingerprint(String fingerprint) {
        return deviceRepository.findByFingerprint(fingerprint);
    }

    public Optional<Device> findByPublicId(UUID publicId) {
        return deviceRepository.findByPublicId(publicId);
    }

    /**
     * Approved devices whose lastSeen is older than the given instant.
     */
    public List<Device> findApprovedLastSeenBefore(Instant threshold) {
        return deviceRepository.findByStateAndLastSeenAtBefore(DeviceState.APPROVED, threshold);
    }

    @Transactional
    public void markOnline(UUID deviceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setLastSeenAt(Instant.now());
            deviceRepository.save(device);
        });
    }

    @Transactional
    public void markOffline(UUID deviceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setLastSeenAt(Instant.EPOCH);
            deviceRepository.save(device);
        });
    }

    @Transactional
    public void touchLastSeen(UUID deviceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            device.setLastSeenAt(Instant.now());
            deviceRepository.save(device);
        });
    }

    @Transactional
    public boolean applyStatusUpdate(UUID deviceId, StatusUpdatePayload payload) {
        Device device = findByDeviceId(deviceId);

        boolean addressChanged = payload.getIpAddress() != null
                && payload.getPort() > 0
                && (!payload.getIpAddress().equals(device.getIpAddress())
                || payload.getPort() != device.getPort());

        if (addressChanged) {
            device.setIpAddress(payload.getIpAddress());
            device.setPort(payload.getPort());
        }

        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);
        return addressChanged;
    }

    /**
     * Check if device was seen recently
     */
    public boolean isOnline(Device device) {
        Instant lastSeen = device.getLastSeenAt();
        return lastSeen != null && lastSeen.isAfter(Instant.now().minus(ONLINE_THRESHOLD));
    }

    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }

    public List<Device> getAllApproved() {
        return getDevicesByState(DeviceState.APPROVED);
    }

    public List<Device> getAllPending() {
        return getDevicesByState(DeviceState.PENDING_APPROVAL);
    }

    public List<Device> getDevicesByState(DeviceState state) {
        return deviceRepository.findByState(state);
    }

    public List<Device> getOnlineDevicesBefore(Instant threshold) {
        return deviceRepository.findByLastSeenAtBefore(threshold);
    }

    public List<Device> getOnlineDevices() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));
        return deviceRepository.findByLastSeenAtAfter(threshold);
    }

    public DeviceStats getDeviceStats() {
        long total = deviceRepository.count();
        Instant threshold = Instant.now().minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));
        long online = deviceRepository.countByLastSeenAtAfter(threshold);
        long pending = deviceRepository.countByState(DeviceState.PENDING_APPROVAL);
        long pendingRename = deviceRepository.countByStateAndRequestedNameIsNotNull(DeviceState.APPROVED);
        long offline = total - online;
        return new DeviceStats(total, online, offline, pending, pendingRename);
    }

    public List<Device> getPendingRenames() {
        return deviceRepository.findByStateAndRequestedNameIsNotNull(DeviceState.APPROVED);
    }

    @Transactional
    public void approveDevice(UUID deviceId) {
        Device device = findByDeviceId(deviceId);
        if (device.getState() != DeviceState.PENDING_APPROVAL) {
            throw new IllegalStateException("Device is not pending approval");
        }

        device.setState(DeviceState.APPROVED);
        device.setPublicId(UUID.randomUUID());
        device.setApprovedAt(Instant.now());

        Device saved = deviceRepository.saveAndFlush(device);

        peerListService.incrementVersion();
        eventPublisher.publishEvent(new DeviceApprovedEvent(deviceMapper.toDTO(saved)));
        log.info("Device approved: {}", saved.getId());
    }

    @Transactional
    public void rejectDevice(UUID deviceId) {
        Device device = findByDeviceId(deviceId);
        if (device.getState() != DeviceState.PENDING_APPROVAL) {
            throw new IllegalStateException("Device is not pending approval");
        }

        device.setState(DeviceState.REJECTED);
        deviceRepository.save(device);
        peerListService.incrementVersion();
        log.info("Device rejected: {}", deviceId);
    }

    @Transactional
    public void requestRename(UUID deviceId, String newName) {
        Device device = findByDeviceId(deviceId);
        if (device.getState() != DeviceState.APPROVED) {
            throw new IllegalStateException("Only approved devices can request rename");
        }

        if (newName == null || newName.isBlank()) {
            throw new InvalidRequestException("Invalid name");
        }

        if (deviceRepository.existsByName(newName)) {
            throw new NameAlreadyInUseException("Name already in use");
        }

        device.setRequestedName(newName);
        device.setRenameRequestedAt(Instant.now());
        deviceRepository.save(device);
        log.info("Rename requested: {} -> {}", device.getName(), newName);
    }

    @Transactional
    public void approveRename(UUID deviceId) {
        Device device = findByDeviceId(deviceId);
        if (device.getRequestedName() == null) {
            throw new IllegalStateException("No pending rename request");
        }

        if (deviceRepository.existsByName(device.getRequestedName())) {
            throw new NameAlreadyInUseException("Name already in use");
        }

        device.setName(device.getRequestedName());
        device.setRequestedName(null);
        device.setRenameRequestedAt(null);

        DeviceDTO deviceDTO = deviceMapper.toDTO(deviceRepository.save(device));
        peerListService.incrementVersion();
        eventPublisher.publishEvent(new DeviceRenamedEvent(deviceDTO));
        log.info("Rename approved for device {}", deviceId);
    }

    @Transactional
    public void rejectRename(UUID deviceId) {
        Device device = findByDeviceId(deviceId);
        if (device.getRequestedName() == null) {
            throw new IllegalStateException("No pending rename request");
        }

        device.setRequestedName(null);
        device.setRenameRequestedAt(null);
        deviceRepository.save(device);
        log.info("Rename rejected for device {}", deviceId);
    }
}