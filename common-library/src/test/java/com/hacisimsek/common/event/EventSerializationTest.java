package com.hacisimsek.common.event;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listeners dispatch on the concrete event class, which only works if the type header written
 * by the producer lets the consumer rebuild that class (configured in shared-defaults.yml).
 */
class EventSerializationTest {

    private static final String TOPIC = "test-events";

    @Test
    void consumerRebuildsConcreteEventTypeFromHeaders() {
        OrderCreatedEvent sent = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderItemDto(UUID.randomUUID(), 2, new BigDecimal("49.90"))), new BigDecimal("99.80"));

        Object received = roundTrip(sent);

        assertThat(received).isInstanceOf(OrderCreatedEvent.class).isEqualTo(sent);
        assertThat(((OrderCreatedEvent) received).getItems()).first().isInstanceOf(OrderItemDto.class);
    }

    @Test
    void sameTopicCarriesDifferentEventTypes() {
        PaymentProcessedEvent sent = new PaymentProcessedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"));

        assertThat(roundTrip(sent)).isInstanceOf(PaymentProcessedEvent.class).isEqualTo(sent);
    }

    private Object roundTrip(Object event) {
        RecordHeaders headers = new RecordHeaders();
        try (JsonSerializer<Object> serializer = new JsonSerializer<>();
             JsonDeserializer<Object> deserializer = new JsonDeserializer<>()) {
            deserializer.configure(Map.of(JsonDeserializer.TRUSTED_PACKAGES, "com.hacisimsek.common.event.*"), false);
            byte[] payload = serializer.serialize(TOPIC, headers, event);
            return deserializer.deserialize(TOPIC, headers, payload);
        }
    }
}
