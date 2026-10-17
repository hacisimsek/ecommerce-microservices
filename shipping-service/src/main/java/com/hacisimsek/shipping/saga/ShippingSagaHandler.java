package com.hacisimsek.shipping.saga;

import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@KafkaListener(groupId = "shipping-service-group", topics = KafkaTopics.PAYMENT_EVENTS)
public class ShippingSagaHandler {

    private final ShippingService shippingService;

    @KafkaHandler
    public void on(PaymentProcessedEvent event) {
        log.info("Received PaymentProcessedEvent for order: {}, processing shipment", event.getOrderId());
        shippingService.processShipping(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event.getClass().getSimpleName());
    }
}
