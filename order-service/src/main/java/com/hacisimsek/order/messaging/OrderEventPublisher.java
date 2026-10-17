package com.hacisimsek.order.messaging;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes saga events to Kafka only after the order transaction commits, so downstream
 * services never react to an order that is not yet visible in the database.
 * (A transactional outbox replaces this in a later phase to also survive broker outages.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Publishing OrderCreatedEvent for order {}", event.getOrderId());
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, event.getOrderId().toString(), event);
    }
}
