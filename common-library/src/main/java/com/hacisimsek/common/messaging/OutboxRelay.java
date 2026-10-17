package com.hacisimsek.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Moves outbox messages to Kafka in write order. A message is deleted only after the broker
 * acknowledged it; if the transaction fails afterwards the message is sent again, which
 * idempotent consumers tolerate (at-least-once delivery).
 */
@Slf4j
public class OutboxRelay {

    private static final String EVENT_PACKAGE = "com.hacisimsek.common.event.";
    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxStore store;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(OutboxStore store, KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper, TransactionTemplate transactionTemplate) {
        this.store = store;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval:500}")
    public void relay() {
        try {
            Integer sent = transactionTemplate.execute(status -> relayBatch());
            if (sent != null && sent > 0) {
                log.debug("Relayed {} outbox message(s)", sent);
            }
        } catch (RuntimeException e) {
            log.warn("Outbox relay failed, will retry: {}", e.getMessage());
        }
    }

    int relayBatch() {
        List<OutboxMessage> batch = store.fetchBatch(BATCH_SIZE);
        for (OutboxMessage message : batch) {
            send(message);
            store.delete(message.id());
        }
        return batch.size();
    }

    private void send(OutboxMessage message) {
        try {
            if (!message.payloadType().startsWith(EVENT_PACKAGE)) {
                throw new IllegalStateException("Refusing to relay untrusted type " + message.payloadType());
            }
            Object event = objectMapper.readValue(message.payload(), Class.forName(message.payloadType()));
            kafkaTemplate.send(message.topic(), message.key(), event).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while relaying outbox message " + message.id(), e);
        } catch (Exception e) {
            // Stop the batch: later messages must not overtake this one
            throw new IllegalStateException("Cannot relay outbox message " + message.id(), e);
        }
    }
}
