package com.mizigo.cargo_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_notifications", uniqueConstraints = {
        @UniqueConstraint(name = "uk_admin_notification_dedup", columnNames = "deduplication_key")
})
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationPriority priority;

    @Column(nullable = false, length = 240)
    private String message;

    @Column(name = "related_type", nullable = false, length = 32)
    private String relatedType;

    @Column(name = "related_id", nullable = false, length = 64)
    private String relatedId;

    @Column(name = "related_link", nullable = false, length = 255)
    private String relatedLink;

    @Column(name = "deduplication_key", nullable = false, length = 160, updatable = false)
    private String deduplicationKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    protected AdminNotification() {}

    public AdminNotification(String type, NotificationPriority priority, String message,
            String relatedType, String relatedId, String relatedLink, String deduplicationKey) {
        this.type = type;
        this.priority = priority;
        this.message = message;
        this.relatedType = relatedType;
        this.relatedId = relatedId;
        this.relatedLink = relatedLink;
        this.deduplicationKey = deduplicationKey;
    }

    @PrePersist
    protected void setCreatedAt() {
        timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public NotificationPriority getPriority() { return priority; }
    public String getMessage() { return message; }
    public String getRelatedType() { return relatedType; }
    public String getRelatedId() { return relatedId; }
    public String getRelatedLink() { return relatedLink; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
