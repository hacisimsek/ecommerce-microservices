package com.hacisimsek.payment.saga;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaHandler {

    private final PaymentService paymentService;

    @KafkaListener(topics = "inventory-events", groupId = "payment-service-group")
    public void handleInventoryEvents(Object event) {
        if (event instanceof InventoryReservedEvent inventoryReservedEvent) {
            log.info("Received InventoryReservedEvent for order: {}", inventoryReservedEvent.getOrderId());
            paymentService.processPayment(inventoryReservedEvent);
        }
    }
}
