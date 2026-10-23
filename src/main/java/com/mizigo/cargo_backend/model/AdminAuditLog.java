package com.mizigo.cargo_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 48)
    private String action;

    @Column(name = "record_type", nullable = false, length = 32)
    private String recordType;

    @Column(name = "record_id", nullable = false, length = 64)
    private String recordId;

    @Column(name = "metadata", length = 1000)
    private String metadata;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    protected AdminAuditLog() {}

    public AdminAuditLog(Long actorId, String action, String recordType,
            String recordId, String metadata) {
        this.actorId = actorId;
        this.action = action;
        this.recordType = recordType;
        this.recordId = recordId;
        this.metadata = metadata;
    }

    @PrePersist
    protected void setCreatedAt() {
        timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getRecordType() { return recordType; }
    public String getRecordId() { return recordId; }
    public String getMetadata() { return metadata; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
