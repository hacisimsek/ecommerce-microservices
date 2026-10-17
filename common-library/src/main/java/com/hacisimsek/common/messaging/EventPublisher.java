package com.hacisimsek.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.BaseEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes saga events through the transactional outbox: the event is written in the same
 * transaction as the business change and {@link OutboxRelay} delivers it to Kafka afterwards.
 * Either both the state change and the event survive, or neither does.
 */
@RequiredArgsConstructor
public class EventPublisher {

    private final OutboxStore outboxStore;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String key, BaseEvent event) {
        try {
            outboxStore.append(topic, key, event.getClass().getName(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize " + event.getClass().getSimpleName(), e);
        }
    }
}
