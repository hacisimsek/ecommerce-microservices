package com.hacisimsek.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private OutboxStore store;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = mock(OutboxStore.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        relay = new OutboxRelay(store, kafkaTemplate, objectMapper, null);
    }

    @Test
    void sendsMessagesInOrderAndDeletesEachAfterTheBrokerAcknowledges() throws Exception {
        OutboxMessage first = message("1");
        OutboxMessage second = message("2");
        when(store.fetchBatch(anyInt())).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(acknowledged());

        assertThat(relay.relayBatch()).isEqualTo(2);

        InOrder order = inOrder(kafkaTemplate, store);
        order.verify(kafkaTemplate).send(eq("payment-events"), eq(first.key()), any(PaymentFailedEvent.class));
        order.verify(store).delete("1");
        order.verify(kafkaTemplate).send(eq("payment-events"), eq(second.key()), any(PaymentFailedEvent.class));
        order.verify(store).delete("2");
    }

    @Test
    void keepsTheMessageWhenSendingFails() throws Exception {
        when(store.fetchBatch(anyInt())).thenReturn(List.of(message("1"), message("2")));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThatThrownBy(relay::relayBatch).hasMessageContaining("Cannot relay outbox message 1");

        verify(store, never()).delete(anyString());
        // The second message must not overtake the first
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void refusesTypesOutsideTheEventPackage() {
        when(store.fetchBatch(anyInt())).thenReturn(List.of(
                new OutboxMessage("1", "payment-events", "k", "java.lang.ProcessBuilder", "{}")));

        assertThatThrownBy(relay::relayBatch).hasMessageContaining("Cannot relay outbox message 1");
        verify(store, never()).delete(anyString());
    }

    private OutboxMessage message(String id) throws Exception {
        PaymentFailedEvent event = new PaymentFailedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "x");
        return new OutboxMessage(id, "payment-events", event.getOrderId().toString(),
                PaymentFailedEvent.class.getName(), objectMapper.writeValueAsString(event));
    }

    private static CompletableFuture<SendResult<String, Object>> acknowledged() {
        return CompletableFuture.completedFuture(null);
    }
}
