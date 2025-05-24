package com.hacisimsek.payment.service.impl;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.payment.model.Payment;
import com.hacisimsek.payment.repository.PaymentRepository;
import com.hacisimsek.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public void processPayment(InventoryReservedEvent event) {
        log.info("Processing payment for order: {}", event.getOrderId());

        // In a real application, we would call a payment gateway
        // Here we'll simulate payment processing with random success/failure

        // First, get the order details (in a real app this would come from a service call or the event)
        // For this example we'll use hard-coded values
        UUID orderId = event.getOrderId();
        UUID customerId = UUID.randomUUID(); // This would come from the order service in real app
        BigDecimal amount = BigDecimal.valueOf(100); // This would come from the order service

        // Create payment record
        Payment payment = Payment.builder()
                .orderId(orderId)
                .customerId(customerId)
                .correlationId(event.getCorrelationId())
                .amount(amount)
                .status(Payment.PaymentStatus.PENDING)
                .paymentMethod("CREDIT_CARD")
                .build();

        // Simulate payment processing
        boolean paymentSuccessful = simulatePaymentGateway();

        if (paymentSuccessful) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setTransactionId(generateTransactionId());
            paymentRepository.save(payment);

            // Send payment success event
            PaymentProcessedEvent processedEvent = new PaymentProcessedEvent(
                    event.getCorrelationId(),
                    orderId,
                    payment.getId()
            );

            kafkaTemplate.send("payment-events", processedEvent);
            log.info("Payment successful for order: {}, transaction ID: {}",
                    orderId, payment.getTransactionId());
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);

            // Send payment failure event
            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    event.getCorrelationId(),
                    orderId,
                    "Payment processing failed"
            );

            kafkaTemplate.send("payment-events", failedEvent);
            log.error("Payment failed for order: {}", orderId);
        }
    }

    @Override
    public Payment getPaymentByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
    }

    private boolean simulatePaymentGateway() {
        // Simulate 90% success rate
        return new Random().nextInt(10) < 9;
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}