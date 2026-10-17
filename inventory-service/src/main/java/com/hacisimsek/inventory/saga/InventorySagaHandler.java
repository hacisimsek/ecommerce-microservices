package com.hacisimsek.inventory.saga;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
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
        topics = {KafkaTopics.ORDER_EVENTS, KafkaTopics.PAYMENT_EVENTS, KafkaTopics.SHIPPING_EVENTS})
public class InventorySagaHandler {

    private final InventoryService inventoryService;

    @KafkaHandler
    public void on(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order: {}", event.getOrderId());
        inventoryService.reserveInventory(event);
    }

    @KafkaHandler
    public void on(PaymentFailedEvent event) {
        log.info("Payment failed for order {}, releasing reservation", event.getOrderId());
        inventoryService.releaseReservation(event, event.getOrderId());
    }

    @KafkaHandler
    public void on(ShipmentFailedEvent event) {
        log.info("Shipment failed for order {}, releasing reservation", event.getOrderId());
        inventoryService.releaseReservation(event, event.getOrderId());
    }

    @KafkaHandler
    public void on(ShipmentProcessedEvent event) {
        log.info("Order {} shipped, confirming reservation", event.getOrderId());
        inventoryService.confirmReservation(event, event.getOrderId());
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event.getClass().getSimpleName());
    }
}
