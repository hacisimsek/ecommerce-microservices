package com.hacisimsek.inventory.saga;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventorySagaHandler {

    private final InventoryService inventoryService;

    // test one pipeline 
    @KafkaListener(topics = "order-events", groupId = "inventory-service-group")
    public void handleOrderEvents(OrderCreatedEvent orderCreatedEvent) {
        log.info("Received OrderCreatedEvent for order: {}", orderCreatedEvent.getOrderId());
        inventoryService.reserveInventory(orderCreatedEvent);
    }

    @KafkaListener(topics = "payment-events", groupId = "inventory-service-group")
    public void handlePaymentEvents(PaymentFailedEvent paymentFailedEvent) {
        log.info("Received PaymentFailedEvent for order: {}", paymentFailedEvent.getOrderId());
        inventoryService.cancelReservation(paymentFailedEvent.getOrderId().toString());
    }
}