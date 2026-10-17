package com.hacisimsek.notification.saga;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(groupId = "notification-service-group", topics = {
        KafkaTopics.ORDER_EVENTS, KafkaTopics.INVENTORY_EVENTS,
        KafkaTopics.PAYMENT_EVENTS, KafkaTopics.SHIPPING_EVENTS})
public class NotificationSagaHandler {

    private final NotificationService notificationService;

    @KafkaHandler
    public void on(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order: {}", event.getOrderId());
        notificationService.sendOrderCreatedNotification(event.getOrderId(), event.getCustomerId());
    }

    @KafkaHandler
    public void on(InventoryReservationFailedEvent event) {
        log.info("Received InventoryReservationFailedEvent for order: {}", event.getOrderId());
        notificationService.sendOrderCancelledNotification(event.getOrderId(), event.getCustomerId(), event.getReason());
    }

    @KafkaHandler
    public void on(PaymentProcessedEvent event) {
        log.info("Received PaymentProcessedEvent for order: {}", event.getOrderId());
        notificationService.sendPaymentSuccessNotification(event.getOrderId(), event.getCustomerId());
    }

    @KafkaHandler
    public void on(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for order: {}", event.getOrderId());
        notificationService.sendPaymentFailedNotification(event.getOrderId(), event.getCustomerId());
    }

    @KafkaHandler
    public void on(ShipmentProcessedEvent event) {
        log.info("Received ShipmentProcessedEvent for order: {}", event.getOrderId());
        notificationService.sendOrderShippedNotification(
                event.getOrderId(), event.getCustomerId(), event.getTrackingNumber());
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event.getClass().getSimpleName());
    }
}
