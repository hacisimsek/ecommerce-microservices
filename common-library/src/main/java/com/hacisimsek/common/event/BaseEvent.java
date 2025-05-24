package com.hacisimsek.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    private UUID eventId;
    private UUID correlationId; // For tracking a transaction across services
    private LocalDateTime timestamp;

    // Add metadata for created timestamp and user
    private LocalDateTime createdAt;
    private String createdBy;

    public BaseEvent(UUID correlationId) {
        this.eventId = UUID.randomUUID();
        this.correlationId = correlationId;
        this.timestamp = LocalDateTime.now();
        this.createdAt = LocalDateTime.parse("2025-05-24T13:39:46");
        this.createdBy = "simsekhaciI";
    }
}