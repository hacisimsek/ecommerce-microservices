package com.hacisimsek.payment.service.impl;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.payment.PaymentRefundedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.common.messaging.EventPublisher;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.common.web.ResourceNotFoundException;
import com.hacisimsek.payment.model.Payment;
import com.hacisimsek.payment.repository.PaymentRepository;
import com.hacisimsek.payment.service.PaymentService;
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
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final EventPublisher eventPublisher;
    private final IdempotencyGuard idempotencyGuard;

    @Value("${payment.simulation.failure-rate:0.0}")
    private double failureRate;

    @Value("${payment.simulation.max-amount:10000}")
    private BigDecimal maxAmount;

    @Override
    @Transactional
    public void processPayment(InventoryReservedEvent event) {
        if (!idempotencyGuard.firstDelivery(event)) {
            return;
        }
        UUID orderId = event.getOrderId();
        log.info("Processing payment of {} for order: {}", event.getTotalAmount(), orderId);

        Payment payment = Payment.builder()
                .orderId(orderId)
                .customerId(event.getCustomerId())
                .correlationId(event.getCorrelationId())
                .amount(event.getTotalAmount())
                .status(Payment.PaymentStatus.PENDING)
                .paymentMethod("CREDIT_CARD")
                .build();

        String failureReason = simulatePaymentGateway(event.getTotalAmount());

        if (failureReason == null) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setTransactionId(generateTransactionId());
            paymentRepository.save(payment);

            publish(orderId, new PaymentProcessedEvent(
                    event.getCorrelationId(), orderId, event.getCustomerId(), payment.getId(), payment.getAmount()));
            log.info("Payment successful for order: {}, transaction ID: {}", orderId, payment.getTransactionId());
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);

            publish(orderId, new PaymentFailedEvent(
                    event.getCorrelationId(), orderId, event.getCustomerId(), failureReason));
            log.warn("Payment failed for order: {}, reason: {}", orderId, failureReason);
        }
    }

    @Override
    @Transactional
    public void refundPayment(ShipmentFailedEvent event) {
        if (!idempotencyGuard.firstDelivery(event)) {
            return;
        }
        UUID orderId = event.getOrderId();
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            log.warn("Nothing to refund for order {} (payment {})", orderId,
                    payment == null ? "missing" : payment.getStatus());
            return;
        }

        // A real provider call goes here; the fake provider always accepts refunds
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        publish(orderId, new PaymentRefundedEvent(
                event.getCorrelationId(), orderId, payment.getCustomerId(), payment.getId(), payment.getAmount()));
        log.info("Refunded {} for order {} after shipping failure", payment.getAmount(), orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId));
    }

    /**
     * Stand-in for a real payment provider. Returns a failure reason, or null on success.
     * Amounts above the limit always fail, which gives a deterministic way to exercise compensation.
     */
    private String simulatePaymentGateway(BigDecimal amount) {
        if (amount.compareTo(maxAmount) > 0) {
            return "Amount " + amount + " exceeds the limit of " + maxAmount;
        }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return "Payment declined by provider";
        }
        return null;
    }

    private void publish(UUID orderId, BaseEvent event) {
        eventPublisher.publish(KafkaTopics.PAYMENT_EVENTS, orderId.toString(), event);
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
