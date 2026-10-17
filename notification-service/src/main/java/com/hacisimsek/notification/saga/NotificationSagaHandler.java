package com.hacisimsek.notification.saga;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.payment.PaymentRefundedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.notification.model.Notification.NotificationType;
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
        notificationService.notify(event, event.getOrderId(), event.getCustomerId(), NotificationType.ORDER_CREATED,
                "Your order has been received",
                "Thank you for your order. We're processing it now.");
    }

    @KafkaHandler
    public void on(InventoryReservationFailedEvent event) {
        notificationService.notify(event, event.getOrderId(), event.getCustomerId(), NotificationType.ORDER_CANCELLED,
                "Your order has been cancelled",
                "We couldn't reserve the items in your order: " + event.getReason());
    }

    @KafkaHandler
    public void on(PaymentProcessedEvent event) {
        notificationService.notify(event, event.getOrderId(), event.getCustomerId(), NotificationType.PAYMENT_SUCCEEDED,
                "Payment successful",
                "Your payment has been processed successfully. Your order is being prepared.");
    }

    @KafkaHandler
    public void on(PaymentFailedEvent event) {
        notificationService.notify(event, event.getOrderId(), event.getCustomerId(), NotificationType.PAYMENT_FAILED,
                "Payment failed",
                "We couldn't process your payment. Please update your payment details.");
    }

    @KafkaHandler
    public void on(PaymentRefundedEvent event) {
        notificationService.notify(event, event.getOrderId(), event.getCustomerId(), NotificationType.PAYMENT_REFUNDED,
                "Your payment has been refunded",
                "We couldn't ship your order, so we refunded " + event.getAmount() + ". We're sorry for the trouble.");
    }

    @KafkaHandler
    public void on(ShipmentProcessedEvent event) {
        notificationService.notify(event, event.getOrderId(), event.getCustomerId(), NotificationType.ORDER_SHIPPED,
                "Your order has been shipped",
                "Your order is on the way! Tracking number: " + event.getTrackingNumber());
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event.getClass().getSimpleName());
    }
}
