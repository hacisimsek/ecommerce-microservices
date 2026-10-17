package com.hacisimsek.common.messaging;

import java.util.UUID;

/** Remembers which events a consumer has already handled. */
public interface ProcessedEventStore {

    /**
     * Records the event as processed by the consumer.
     *
     * @return {@code true} on first delivery, {@code false} if it was processed before
     */
    boolean markProcessed(UUID eventId, String consumer);
}
