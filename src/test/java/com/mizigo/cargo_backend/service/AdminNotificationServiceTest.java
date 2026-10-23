package com.mizigo.cargo_backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mizigo.cargo_backend.model.AdminNotification;
import com.mizigo.cargo_backend.model.NotificationPriority;
import com.mizigo.cargo_backend.repository.AdminNotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminNotificationServiceTest {

    @Mock
    private AdminNotificationRepository notificationRepository;

    @InjectMocks
    private AdminNotificationService notificationService;

    @Test
    void doesNotCreateNotificationWhenDeduplicationKeyExists() {
        when(notificationRepository.existsByDeduplicationKey("DRIVER_REGISTERED:9"))
                .thenReturn(true);

        notificationService.notifyOnce("DRIVER_REGISTERED", NotificationPriority.MEDIUM,
                "A driver registered.", "DRIVER", "9", "/admin-dashboard",
                "DRIVER_REGISTERED:9");

        verify(notificationRepository, never()).save(any(AdminNotification.class));
    }

    @Test
    void persistsNewNotificationWithDeduplicationKey() {
        when(notificationRepository.existsByDeduplicationKey("DRIVER_REGISTERED:9"))
                .thenReturn(false);

        notificationService.notifyOnce("DRIVER_REGISTERED", NotificationPriority.MEDIUM,
                "A driver registered.", "DRIVER", "9", "/admin-dashboard",
                "DRIVER_REGISTERED:9");

        verify(notificationRepository).save(any(AdminNotification.class));
    }
}
