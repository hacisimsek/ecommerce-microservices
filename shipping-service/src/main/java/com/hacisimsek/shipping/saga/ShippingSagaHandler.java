package com.hacisimsek.shipping.saga;

import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingSagaHandler {

    private final ShippingService shippingService;

    @KafkaListener(topics = "payment-events", groupId = "shipping-service-group")
    public void handlePaymentEvents(Object event) {
        if (event instanceof PaymentProcessedEvent paymentProcessedEvent) {
            log.info("Received PaymentProcessedEvent for order: {}, processing shipment",
                    paymentProcessedEvent.getOrderId());
            shippingService.processShipping(paymentProcessedEvent);
        }
    }
}