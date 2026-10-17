package com.hacisimsek.notification.service.impl;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.notification.model.Notification;
import com.hacisimsek.notification.repository.NotificationRepository;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public void notify(BaseEvent cause, UUID orderId, UUID customerId, Notification.NotificationType type,
                       String subject, String message) {
        if (!idempotencyGuard.firstDelivery(cause)) {
            return;
        }
        // A real channel (email/SMS/push) plugs in here; for now the notification is only recorded
        log.info("Sending {} notification to recipient: {}, for order: {}", type, customerId, orderId);
        notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(customerId)
                .orderId(orderId)
                .subject(subject)
                .message(message)
                .type(type)
                .status(Notification.NotificationStatus.SENT)
                .createdAt(LocalDateTime.now())
                .createdBy("system")
                .sentAt(LocalDateTime.now())
                .build());
    }

    @Override
    public List<Notification> getNotificationsByRecipient(UUID recipientId) {
        return notificationRepository.findByRecipientId(recipientId);
    }

    @Override
    public List<Notification> getNotificationsByOrder(UUID orderId) {
        return notificationRepository.findByOrderId(orderId);
    }
}
