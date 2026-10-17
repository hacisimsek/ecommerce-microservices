package com.hacisimsek.shipping.service.impl;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.common.messaging.EventPublisher;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.common.web.ResourceNotFoundException;
import com.hacisimsek.shipping.model.Shipment;
import com.hacisimsek.shipping.repository.ShipmentRepository;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingServiceImpl implements ShippingService {

    private static final String[] CARRIERS = {"DHL", "FedEx", "UPS", "USPS"};
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final ShipmentRepository shipmentRepository;
    private final EventPublisher eventPublisher;
    private final IdempotencyGuard idempotencyGuard;

    @Value("${shipping.simulation.max-insured-value:5000}")
    private BigDecimal maxInsuredValue;

    /**
     * Business rejections become a {@link ShipmentFailedEvent}; unexpected exceptions propagate
     * so the Kafka error handler retries the record and finally parks it on the dead letter topic.
     */
    @Override
    @Transactional
    public void processShipping(PaymentProcessedEvent paymentEvent) {
        if (!idempotencyGuard.firstDelivery(paymentEvent)) {
            return;
        }
        UUID orderId = paymentEvent.getOrderId();
        log.info("Processing shipping for order: {}", orderId);

        String rejection = checkShippable(paymentEvent.getAmount());
        if (rejection != null) {
            publish(orderId, new ShipmentFailedEvent(
                    paymentEvent.getCorrelationId(), orderId, paymentEvent.getCustomerId(), rejection));
            log.warn("Cannot ship order {}: {}", orderId, rejection);
            return;
        }

        // Address details arrive with checkout in a later phase; placeholders until then
        Shipment shipment = shipmentRepository.save(Shipment.builder()
                .orderId(orderId)
                .customerId(paymentEvent.getCustomerId())
                .correlationId(paymentEvent.getCorrelationId())
                .status(Shipment.ShipmentStatus.SHIPPED)
                .carrierName(CARRIERS[ThreadLocalRandom.current().nextInt(CARRIERS.length)])
                .trackingNumber(generateTrackingNumber())
                .shippedDate(LocalDateTime.now())
                .estimatedDeliveryDate(LocalDateTime.now().plusDays(3))
                .shippingAddress("123 Main St, New York, NY 10001")
                .recipientName("John Doe")
                .recipientPhone("(212) 555-1234")
                .build());

        publish(orderId, new ShipmentProcessedEvent(paymentEvent.getCorrelationId(), orderId,
                paymentEvent.getCustomerId(), shipment.getId(), shipment.getTrackingNumber()));
        log.info("Order shipped. Order ID: {}, Tracking: {}", orderId, shipment.getTrackingNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Shipment getShipmentByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment for order", orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public Shipment getShipmentById(UUID shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", shipmentId));
    }

    /**
     * Stand-in for carrier rules. Parcels above the insured value are rejected, which gives a
     * deterministic way to exercise the refund + stock release compensation.
     */
    private String checkShippable(BigDecimal amount) {
        if (amount != null && amount.compareTo(maxInsuredValue) > 0) {
            return "Order value " + amount + " exceeds the carrier's insured limit of " + maxInsuredValue;
        }
        return null;
    }

    private void publish(UUID orderId, BaseEvent event) {
        eventPublisher.publish(KafkaTopics.SHIPPING_EVENTS, orderId.toString(), event);
    }

    /** Format: 2 letters + 9 digits + 2 letters. */
    private String generateTrackingNumber() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            sb.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }
        for (int i = 0; i < 2; i++) {
            sb.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return sb.toString();
    }
}
