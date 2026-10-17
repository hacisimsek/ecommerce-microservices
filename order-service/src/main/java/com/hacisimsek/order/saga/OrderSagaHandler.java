package com.hacisimsek.order.saga;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(groupId = "order-service-group",
        topics = {KafkaTopics.INVENTORY_EVENTS, KafkaTopics.PAYMENT_EVENTS, KafkaTopics.SHIPPING_EVENTS})
public class OrderSagaHandler {

    private final OrderService orderService;

    @KafkaHandler
    public void on(InventoryReservedEvent event) {
        log.info("Inventory reserved for order: {}", event.getOrderId());
        orderService.updateOrderStatus(event, event.getOrderId(), Order.OrderStatus.INVENTORY_RESERVED);
    }

    @KafkaHandler
    public void on(InventoryReservationFailedEvent event) {
        log.warn("Inventory reservation failed for order: {}, reason: {}", event.getOrderId(), event.getReason());
        orderService.updateOrderStatus(event, event.getOrderId(), Order.OrderStatus.CANCELLED);
    }

    @KafkaHandler
    public void on(PaymentProcessedEvent event) {
        log.info("Payment processed for order: {}", event.getOrderId());
        orderService.updateOrderStatus(event, event.getOrderId(), Order.OrderStatus.PAYMENT_COMPLETED);
    }

    @KafkaHandler
    public void on(PaymentFailedEvent event) {
        log.warn("Payment failed for order: {}, reason: {}", event.getOrderId(), event.getReason());
        orderService.updateOrderStatus(event, event.getOrderId(), Order.OrderStatus.FAILED);
    }

    @KafkaHandler
    public void on(ShipmentProcessedEvent event) {
        log.info("Order shipped: {}, tracking number: {}", event.getOrderId(), event.getTrackingNumber());
        orderService.updateOrderStatus(event, event.getOrderId(), Order.OrderStatus.SHIPPED);
    }

    @KafkaHandler
    public void on(ShipmentFailedEvent event) {
        log.warn("Shipping failed for order: {}, reason: {}", event.getOrderId(), event.getReason());
        orderService.updateOrderStatus(event, event.getOrderId(), Order.OrderStatus.FAILED);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event.getClass().getSimpleName());
    }
}
