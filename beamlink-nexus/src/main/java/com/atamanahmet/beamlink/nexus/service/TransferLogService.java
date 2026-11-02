package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Device;
import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.dto.TransferStats;
import com.atamanahmet.beamlink.nexus.repository.TransferLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferLogService {

    private final TransferLogRepository transferLogRepository;
    private final DeviceService deviceService;
    private final NexusService nexusService;

    @Transactional
    public List<UUID> sync(UUID deviceId, List<LogSyncRequest> incoming) {
        List<TransferLog> toSave = incoming.stream()
                .filter(r -> r.getId() != null)
                .filter(r -> !transferLogRepository.existsById(r.getId()))
                .map(r -> {
                    UUID fromId = r.getFromDeviceId();
                    return TransferLog.builder()
                            .id(r.getId())
                            .fromDeviceId(fromId)
                            .fromDeviceName(resolveFromDeviceName(fromId, r.getFromDeviceName()))
                            .toDeviceId(r.getToDeviceId())
                            .toDeviceName(r.getToDeviceName())
                            .filename(r.getFilename())
                            .fileSize(r.getFileSize())
                            .averageSpeedMbps(r.getAverageSpeedMbps())
                            .timestamp(r.getTimestamp() != null ? r.getTimestamp() : Instant.now())
                            .build();
                })
                .toList();

        if (!toSave.isEmpty()) {
            transferLogRepository.saveAll(toSave);
            log.info("Synced {}/{} new logs from device {}.", toSave.size(), incoming.size(), deviceId);
        }

        return incoming.stream()
                .map(LogSyncRequest::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    public void logTransfer(UUID fromDeviceId, String fromDeviceName, String filename, long fileSize, double averageSpeedMbps) {
        TransferLog entry = TransferLog.builder()
                .id(UUID.randomUUID())
                .fromDeviceId(fromDeviceId)
                .fromDeviceName(resolveFromDeviceName(fromDeviceId, fromDeviceName))
                .toDeviceId(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .toDeviceName("Nexus")
                .filename(filename)
                .fileSize(fileSize)
                .averageSpeedMbps(averageSpeedMbps)
                .timestamp(Instant.now())
                .build();

        transferLogRepository.save(entry);
    }

    public List<TransferLog> getLogs(Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        );
        return transferLogRepository.findAll(sorted).getContent();
    }

    public List<TransferLog> getRecentLogs(int limit) {
        return getLogs(PageRequest.of(0, limit));
    }

    public TransferStats getTransferStats() {
        long totalTransfers = transferLogRepository.count();
        Long totalSize = transferLogRepository.sumFileSize();
        long totalDataTransferred = totalSize != null ? totalSize : 0L;
        return new TransferStats(totalTransfers, totalDataTransferred);
    }

    /**
     * Deletes every transfer log row.
     */
    @Transactional
    public void deleteAll() {
        transferLogRepository.deleteAll();
    }

    /**
     * Fills a missing source name from the device table or local nexus name.
     */
    private String resolveFromDeviceName(UUID fromDeviceId, String provided) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        if (fromDeviceId == null) {
            return null;
        }
        UUID nexusId = nexusService.getNexusId();
        if (nexusId != null && nexusId.equals(fromDeviceId)) {
            return nexusService.getNexusName();
        }
        return deviceService.findOptional(fromDeviceId)
                .map(Device::getName)
                .orElse(null);
    }
}
