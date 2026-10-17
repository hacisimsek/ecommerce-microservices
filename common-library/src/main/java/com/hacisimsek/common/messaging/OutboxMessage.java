package com.hacisimsek.common.messaging;

/**
 * An event waiting in the outbox to be relayed to Kafka.
 *
 * @param id          store-specific identifier; ordering by it preserves write order
 * @param topic       destination topic
 * @param key         Kafka message key (the order id, so one order's events stay in one partition)
 * @param payloadType fully qualified event class name
 * @param payload     event serialized as JSON
 */
public record OutboxMessage(String id, String topic, String key, String payloadType, String payload) {
}
