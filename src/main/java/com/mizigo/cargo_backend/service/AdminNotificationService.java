package com.mizigo.cargo_backend.service;

import com.mizigo.cargo_backend.model.AdminNotification;
import com.mizigo.cargo_backend.model.NotificationPriority;
import com.mizigo.cargo_backend.repository.AdminNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminNotificationService {

    private final AdminNotificationRepository notificationRepository;

    public AdminNotificationService(AdminNotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notifyOnce(String type, NotificationPriority priority, String message,
            String relatedType, String relatedId, String relatedLink, String deduplicationKey) {
        if (!notificationRepository.existsByDeduplicationKey(deduplicationKey)) {
            notificationRepository.save(new AdminNotification(type, priority, message,
                    relatedType, relatedId, relatedLink, deduplicationKey));
        }
    }
}
