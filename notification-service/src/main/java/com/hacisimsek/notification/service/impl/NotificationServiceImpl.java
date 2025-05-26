package com.hacisimsek.notification.service.impl;

import com.hacisimsek.notification.model.Notification;
import com.hacisimsek.notification.repository.NotificationRepository;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public void sendOrderCreatedNotification(UUID orderId, UUID customerId) {
        Notification notification = createNotification(
                customerId,
                orderId,
                "Your order has been received",
                "Thank you for your order. We're processing it now.",
                Notification.NotificationType.ORDER_CREATED
        );

        sendNotification(notification);
    }

    @Override
    public void sendPaymentSuccessNotification(UUID orderId, UUID customerId) {
        Notification notification = createNotification(
                customerId,
                orderId,
                "Payment successful",
                "Your payment has been processed successfully. Your order is being prepared.",
                Notification.NotificationType.PAYMENT_SUCCEEDED
        );

        sendNotification(notification);
    }

    @Override
    public void sendPaymentFailedNotification(UUID orderId, UUID customerId) {
        Notification notification = createNotification(
                customerId,
                orderId,
                "Payment failed",
                "We couldn't process your payment. Please update your payment details.",
                Notification.NotificationType.PAYMENT_FAILED
        );

        sendNotification(notification);
    }

    @Override
    public void sendOrderShippedNotification(UUID orderId, UUID customerId, String trackingNumber) {
        Notification notification = createNotification(
                customerId,
                orderId,
                "Your order has been shipped",
                "Your order is on the way! Tracking number: " + trackingNumber,
                Notification.NotificationType.ORDER_SHIPPED
        );

        sendNotification(notification);
    }

    @Override
    public List<Notification> getNotificationsByRecipient(UUID recipientId) {
        return notificationRepository.findByRecipientId(recipientId);
    }

    @Override
    public List<Notification> getNotificationsByOrder(UUID orderId) {
        return notificationRepository.findByOrderId(orderId);
    }

    private Notification createNotification(UUID recipientId, UUID orderId, String subject, String message, Notification.NotificationType type) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(recipientId)
                .orderId(orderId)
                .subject(subject)
                .message(message)
                .type(type)
                .status(Notification.NotificationStatus.PENDING)
                .createdAt(LocalDateTime.parse("2025-05-24T16:33:57", DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .createdBy("simsekhaci")
                .build();
    }

    private void sendNotification(Notification notification) {
        // In a real application, this would connect to an email/SMS/push notification service
        log.info("Sending {} notification to recipient: {}, for order: {}",
                notification.getType(), notification.getRecipientId(), notification.getOrderId());

        // Update notification status
        notification.setStatus(Notification.NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());

        notificationRepository.save(notification);
        log.info("Notification sent successfully");
    }
}