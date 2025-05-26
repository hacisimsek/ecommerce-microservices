package com.hacisimsek.notification.saga;

import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSagaHandler {

    private final NotificationService notificationService;

    @KafkaListener(topics = "order-events", groupId = "notification-service-group")
    public void handleOrderEvents(Object event) {
        if (event instanceof OrderCreatedEvent orderCreatedEvent) {
            log.info("Received OrderCreatedEvent for order: {}", orderCreatedEvent.getOrderId());
            notificationService.sendOrderCreatedNotification(
                    orderCreatedEvent.getOrderId(),
                    orderCreatedEvent.getCustomerId()
            );
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-service-group")
    public void handlePaymentEvents(Object event) {
        if (event instanceof PaymentProcessedEvent paymentProcessedEvent) {
            log.info("Received PaymentProcessedEvent for order: {}", paymentProcessedEvent.getOrderId());
            // In a real application, we'd fetch customer ID from order service
            // For now, using a dummy UUID
            notificationService.sendPaymentSuccessNotification(
                    paymentProcessedEvent.getOrderId(),
                    UUID.randomUUID()
            );
        } else if (event instanceof PaymentFailedEvent paymentFailedEvent) {
            log.info("Received PaymentFailedEvent for order: {}", paymentFailedEvent.getOrderId());
            // In a real application, we'd fetch customer ID from order service
            notificationService.sendPaymentFailedNotification(
                    paymentFailedEvent.getOrderId(),
                    UUID.randomUUID()
            );
        }
    }

    @KafkaListener(topics = "shipping-events", groupId = "notification-service-group")
    public void handleShippingEvents(Object event) {
        if (event instanceof ShipmentProcessedEvent shipmentProcessedEvent) {
            log.info("Received ShipmentProcessedEvent for order: {}", shipmentProcessedEvent.getOrderId());
            // In a real application, we'd fetch customer ID from order service
            notificationService.sendOrderShippedNotification(
                    shipmentProcessedEvent.getOrderId(),
                    UUID.randomUUID(),
                    shipmentProcessedEvent.getTrackingNumber()
            );
        }
    }
}