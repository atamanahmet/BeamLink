package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
