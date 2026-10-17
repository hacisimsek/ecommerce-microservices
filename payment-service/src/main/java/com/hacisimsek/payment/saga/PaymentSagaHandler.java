package com.hacisimsek.payment.saga;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(groupId = "payment-service-group", topics = KafkaTopics.INVENTORY_EVENTS)
public class PaymentSagaHandler {

    private final PaymentService paymentService;

    @KafkaHandler
    public void on(InventoryReservedEvent event) {
        log.info("Received InventoryReservedEvent for order: {}", event.getOrderId());
        paymentService.processPayment(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event.getClass().getSimpleName());
    }
}
