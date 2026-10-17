package com.hacisimsek.notification.service;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.notification.model.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    /**
     * Records and sends a customer notification triggered by a saga event.
     * A redelivered event does not notify the customer twice.
     */
    void notify(BaseEvent cause, UUID orderId, UUID customerId, Notification.NotificationType type,
                String subject, String message);

    List<Notification> getNotificationsByRecipient(UUID recipientId);

    List<Notification> getNotificationsByOrder(UUID orderId);
}
