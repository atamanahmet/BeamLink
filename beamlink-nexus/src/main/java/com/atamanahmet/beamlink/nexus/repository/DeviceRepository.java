package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.enums.DeviceState;
import com.atamanahmet.beamlink.nexus.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID>{
    List<Device> findByState(DeviceState state);

    Optional<Device> findByFingerprint(String fingerprint);

    List<Device> findByLastSeenAtAfter(Instant threshold);

    List<Device> findByLastSeenAtBefore(Instant threshold);

    List<Device> findByStateAndRequestedNameIsNotNull(DeviceState state);

    List<Device> findByStateAndLastSeenAtBefore(DeviceState state, Instant threshold);

    Optional<Device> findByPublicId(UUID publicId);

    Optional<Device> findByIpAddressAndPort(String ipAddress, int port);

    Optional<Device> findByIpAddressAndTlsPort(String ipAddress, Integer tlsPort);

    boolean existsByName(String deviceName);

    long countByLastSeenAtAfter(Instant threshold);
    long countByLastSeenAtBefore(Instant threshold);
    long countByState(DeviceState state);
    long countByStateAndRequestedNameIsNotNull(DeviceState state);
}
