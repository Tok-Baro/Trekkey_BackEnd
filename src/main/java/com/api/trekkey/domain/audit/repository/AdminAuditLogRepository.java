package com.api.trekkey.domain.audit.repository;

import com.api.trekkey.domain.audit.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
