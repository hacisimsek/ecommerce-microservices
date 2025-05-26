package com.hacisimsek.shipping.service.impl;

import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.shipping.model.Shipment;
import com.hacisimsek.shipping.repository.ShipmentRepository;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingServiceImpl implements ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String[] CARRIERS = {"DHL", "FedEx", "UPS", "USPS"};

    @Override
    @Transactional
    public void processShipping(PaymentProcessedEvent paymentEvent) {
        log.info("Processing shipping for order: {}", paymentEvent.getOrderId());

        // In a real application, we would retrieve shipping address from order service
        // For this example, we'll create a shipment with minimal information

        try {
            // Create the shipment record
            Shipment shipment = Shipment.builder()
                    .orderId(paymentEvent.getOrderId())
                    .customerId(UUID.randomUUID()) // In a real app this would come from order
                    .correlationId(paymentEvent.getCorrelationId())
                    .status(Shipment.ShipmentStatus.PROCESSING)
                    .carrierName(getRandomCarrier())
                    .trackingNumber(generateTrackingNumber())
                    .shippedDate(LocalDateTime.now())
                    .estimatedDeliveryDate(LocalDateTime.now().plusDays(3))
                    // Minimal shipping details for demo
                    .shippingAddress("123 Main St, New York, NY 10001")
                    .recipientName("John Doe")
                    .recipientPhone("(212) 555-1234")
                    .build();

            Shipment savedShipment = shipmentRepository.save(shipment);

            // Update shipment status to shipped
            savedShipment.setStatus(Shipment.ShipmentStatus.SHIPPED);
            shipmentRepository.save(savedShipment);

            // Send shipment processed event
            ShipmentProcessedEvent shipmentEvent = new ShipmentProcessedEvent(
                    paymentEvent.getCorrelationId(),
                    paymentEvent.getOrderId(),
                    savedShipment.getId(),
                    savedShipment.getTrackingNumber()
            );

            kafkaTemplate.send("shipping-events", shipmentEvent);
            log.info("Order shipped successfully. Order ID: {}, Tracking: {}",
                    paymentEvent.getOrderId(), savedShipment.getTrackingNumber());

        } catch (Exception e) {
            log.error("Failed to process shipping for order: {}", paymentEvent.getOrderId(), e);

            // Send shipment failure event
            ShipmentFailedEvent failedEvent = new ShipmentFailedEvent(
                    paymentEvent.getCorrelationId(),
                    paymentEvent.getOrderId(),
                    "Failed to process shipment: " + e.getMessage()
            );

            kafkaTemplate.send("shipping-events", failedEvent);
        }
    }

    @Override
    public Shipment getShipmentByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Shipment not found for order: " + orderId));
    }

    @Override
    public Shipment getShipmentById(UUID shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment not found with ID: " + shipmentId));
    }

    private String getRandomCarrier() {
        return CARRIERS[new Random().nextInt(CARRIERS.length)];
    }

    private String generateTrackingNumber() {
        // Format: 2 letters + 9 digits + 2 letters
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();

        Random random = new Random();

        // First 2 letters
        for (int i = 0; i < 2; i++) {
            sb.append(letters.charAt(random.nextInt(letters.length())));
        }

        // 9 digits
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }

        // Last 2 letters
        for (int i = 0; i < 2; i++) {
            sb.append(letters.charAt(random.nextInt(letters.length())));
        }

        return sb.toString();
    }
}