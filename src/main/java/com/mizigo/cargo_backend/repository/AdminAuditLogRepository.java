package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.AdminAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findTop100ByOrderByTimestampDesc();
    List<AdminAuditLog> findTop100ByRecordTypeAndRecordIdOrderByTimestampDesc(
            String recordType, String recordId);
}
