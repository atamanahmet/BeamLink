package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.atamanahmet.beamlink.agent.domain.enums.TransferSyncState;
import com.atamanahmet.beamlink.agent.dto.TransferLogDTO;
import com.atamanahmet.beamlink.agent.mapper.TransferLogMapper;
import com.atamanahmet.beamlink.agent.repository.TransferLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferLogService {

    private static final Logger log = LoggerFactory.getLogger(TransferLogService.class);

    private final TransferLogRepository repository;

    /** Persists a new transfer log entry. Always starts as PENDING until synced. */
    @Transactional
    public void logTransfer(TransferLogDTO dto) {
        repository.save(TransferLogMapper.toEntity(dto));
    }

    public List<TransferLogDTO> getUnsyncedLogs() {
        return repository.findBySyncState(TransferSyncState.PENDING)
                .stream()
                .map(TransferLogMapper::toDTO)
                .toList();
    }

    /** Marks logs as synced after successful push to Nexus. */
    @Transactional
    public void markAsSynced(List<UUID> ids) {
        List<TransferLog> logs = repository.findAllById(ids);
        logs.forEach(l -> l.setSyncState(TransferSyncState.SYNCED));
        repository.saveAll(logs);
    }
}