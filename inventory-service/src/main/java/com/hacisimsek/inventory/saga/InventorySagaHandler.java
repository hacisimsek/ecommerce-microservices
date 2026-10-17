package com.hacisimsek.inventory.saga;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(groupId = "inventory-service-group",
        topics = {KafkaTopics.ORDER_EVENTS, KafkaTopics.PAYMENT_EVENTS})
public class InventorySagaHandler {

    private final InventoryService inventoryService;

    @KafkaHandler
    public void on(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order: {}", event.getOrderId());
        inventoryService.reserveInventory(event);
    }

    @KafkaHandler
    public void on(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for order: {}, releasing reservation", event.getOrderId());
        inventoryService.cancelReservation(event.getOrderId());
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event.getClass().getSimpleName());
    }
}
