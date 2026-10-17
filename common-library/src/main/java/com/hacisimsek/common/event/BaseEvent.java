package com.hacisimsek.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    private UUID eventId;
    private UUID correlationId; // For tracking a transaction across services
    private Instant timestamp;

    protected BaseEvent(UUID correlationId) {
        this.eventId = UUID.randomUUID();
        this.correlationId = correlationId;
        this.timestamp = Instant.now();
    }
}
