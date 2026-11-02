package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.AdminAuditLog;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditEventType;
import com.atamanahmet.beamlink.nexus.domain.enums.AuditOutcome;
import com.atamanahmet.beamlink.nexus.dto.AuditLogResponse;
import com.atamanahmet.beamlink.nexus.repository.AdminAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class AuditService {

    static final int MAX_ROWS = 10_000;

    private static final int DETAIL_MAX = 200;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final PlatformTransactionManager transactionManager;
    private final int maxRows;

    @Autowired
    public AuditService(
            AdminAuditLogRepository adminAuditLogRepository,
            PlatformTransactionManager transactionManager) {
        this(adminAuditLogRepository, transactionManager, MAX_ROWS);
    }

    AuditService(
            AdminAuditLogRepository adminAuditLogRepository,
            PlatformTransactionManager transactionManager,
            int maxRows) {
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.transactionManager = transactionManager;
        this.maxRows = maxRows;
    }

    /**
     * Inserts one row then drops oldest by id over the cap. Failures are logged, never thrown.
     */
    public void record(
            AuditEventType type,
            AuditOutcome outcome,
            String sourceAddress,
            String targetDeviceId,
            String detail) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                AdminAuditLog row = new AdminAuditLog();
                row.setOccurredAt(Instant.now());
                row.setEventType(type);
                row.setOutcome(outcome);
                row.setSourceAddress(sourceAddress);
                row.setTargetDeviceId(targetDeviceId);
                row.setDetail(cutDetail(detail));
                adminAuditLogRepository.save(row);
                trim();
            });
        } catch (RuntimeException e) {
            log.error("Audit log write failed", e);
        }
    }

    /**
     * Paged rows, newest id first.
     */
    public List<AuditLogResponse> list(int page, int size) {
        return adminAuditLogRepository.findAll(
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .getContent()
                .stream()
                .map(AuditService::toResponse)
                .toList();
    }

    /**
     * Deletes oldest rows by id until count is at the cap.
     */
    private void trim() {
        long extra = adminAuditLogRepository.count() - maxRows;
        if (extra <= 0) {
            return;
        }
        var oldest = adminAuditLogRepository.findAll(
                PageRequest.of(0, (int) extra, Sort.by(Sort.Direction.ASC, "id")));
        adminAuditLogRepository.deleteAll(oldest.getContent());
    }

    private static String cutDetail(String detail) {
        if (detail == null || detail.length() <= DETAIL_MAX) {
            return detail;
        }
        return detail.substring(0, DETAIL_MAX);
    }

    private static AuditLogResponse toResponse(AdminAuditLog row) {
        return new AuditLogResponse(
                row.getId(),
                row.getOccurredAt(),
                row.getEventType(),
                row.getOutcome(),
                row.getSourceAddress(),
                row.getTargetDeviceId(),
                row.getDetail());
    }
}
