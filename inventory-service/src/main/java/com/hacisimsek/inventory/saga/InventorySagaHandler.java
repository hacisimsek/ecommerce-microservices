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

    @KafkaListener(topics = "order-events", groupId = "inventory-service-group")
    public void handleOrderEvents(Object event) {
        if (event instanceof OrderCreatedEvent orderCreatedEvent) {
            log.info("Received OrderCreatedEvent for order: {}", orderCreatedEvent.getOrderId());
            inventoryService.reserveInventory(orderCreatedEvent);
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "inventory-service-group")
    public void handlePaymentEvents(Object event) {
        if (event instanceof PaymentFailedEvent paymentFailedEvent) {
            log.info("Received PaymentFailedEvent for order: {}, cancelling inventory reservation",
                    paymentFailedEvent.getOrderId());
            inventoryService.cancelReservation(paymentFailedEvent.getOrderId());
        }
    }
}