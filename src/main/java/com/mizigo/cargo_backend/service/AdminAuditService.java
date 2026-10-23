package com.mizigo.cargo_backend.service;

import com.mizigo.cargo_backend.model.AdminAuditLog;
import com.mizigo.cargo_backend.repository.AdminAuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    public AdminAuditService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(Long actorId, String action, String recordType,
            String recordId, String metadata) {
        auditLogRepository.save(new AdminAuditLog(actorId, action, recordType, recordId, metadata));
    }
}
