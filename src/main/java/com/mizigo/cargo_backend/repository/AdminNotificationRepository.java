package com.mizigo.cargo_backend.repository;

import com.mizigo.cargo_backend.model.AdminNotification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {
    boolean existsByDeduplicationKey(String deduplicationKey);
    List<AdminNotification> findTop100ByOrderByTimestampDesc();
    long countByReadFalse();

    @Modifying
    @Query("update AdminNotification notification set notification.read = true where notification.read = false")
    int markAllRead();
}
