package com.hacisimsek.common.messaging;

import com.hacisimsek.common.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka delivers at least once. Call {@link #firstDelivery} at the start of a transactional
 * handler: the processed marker commits or rolls back together with the handler's changes.
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final ProcessedEventStore store;
    private final String consumer;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(BaseEvent event) {
        boolean first = store.markProcessed(event.getEventId(), consumer);
        if (!first) {
            log.info("Skipping duplicate {} {}", event.getClass().getSimpleName(), event.getEventId());
        }
        return first;
    }
}
