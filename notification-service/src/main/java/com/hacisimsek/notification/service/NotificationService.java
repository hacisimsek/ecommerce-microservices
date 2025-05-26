package com.hacisimsek.notification.service;

import com.hacisimsek.notification.model.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    void sendOrderCreatedNotification(UUID orderId, UUID customerId);
    void sendPaymentSuccessNotification(UUID orderId, UUID customerId);
    void sendPaymentFailedNotification(UUID orderId, UUID customerId);
    void sendOrderShippedNotification(UUID orderId, UUID customerId, String trackingNumber);
    List<Notification> getNotificationsByRecipient(UUID recipientId);
    List<Notification> getNotificationsByOrder(UUID orderId);
}