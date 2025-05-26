package com.hacisimsek.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    private UUID id;
    private UUID recipientId;
    private UUID orderId;
    private String message;
    private String subject;
    private NotificationType type;
    private NotificationStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime sentAt;

    public enum NotificationType {
        ORDER_CREATED, ORDER_CONFIRMED, PAYMENT_SUCCEEDED, PAYMENT_FAILED, ORDER_SHIPPED, ORDER_DELIVERED
    }

    public enum NotificationStatus {
        PENDING, SENT, FAILED
    }
}