package com.hacisimsek.inventory.service.impl;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.common.messaging.EventPublisher;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryReservation;
import com.hacisimsek.inventory.model.InventoryReservation.ReservationStatus;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.repository.ReservationRepository;
import com.hacisimsek.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final EventPublisher eventPublisher;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public void reserveInventory(OrderCreatedEvent event) {
        if (!idempotencyGuard.firstDelivery(event)) {
            return;
        }
        UUID orderId = event.getOrderId();
        log.info("Processing inventory reservation for order: {}", orderId);

        List<InventoryItem> items = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (var orderItem : event.getItems()) {
            Optional<InventoryItem> item = inventoryRepository.findById(orderItem.getProductId());
            if (item.isEmpty()) {
                problems.add("Product not found: " + orderItem.getProductId());
            } else if (item.get().getAvailableQuantity() < orderItem.getQuantity()) {
                problems.add("Insufficient quantity for product " + orderItem.getProductId()
                        + ": requested " + orderItem.getQuantity()
                        + ", available " + item.get().getAvailableQuantity());
            } else {
                items.add(item.get());
            }
        }

        if (!problems.isEmpty()) {
            String reason = String.join("; ", problems);
            eventPublisher.publish(KafkaTopics.INVENTORY_EVENTS, orderId.toString(),
                    new InventoryReservationFailedEvent(event.getCorrelationId(), orderId, event.getCustomerId(), reason));
            log.warn("Inventory reservation failed for order {}: {}", orderId, reason);
            return;
        }

        List<InventoryReservation.ReservationItem> reserved = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            InventoryItem item = items.get(i);
            int quantity = event.getItems().get(i).getQuantity();
            item.setAvailableQuantity(item.getAvailableQuantity() - quantity);
            item.setReservedQuantity(item.getReservedQuantity() + quantity);
            // @Version makes a concurrent change to the same item fail and be retried
            inventoryRepository.save(item);
            reserved.add(InventoryReservation.ReservationItem.builder()
                    .productId(item.getId())
                    .quantity(quantity)
                    .build());
        }

        reservationRepository.save(InventoryReservation.builder()
                .id(UUID.randomUUID())
                .orderId(orderId)
                .correlationId(event.getCorrelationId())
                .items(reserved)
                .status(ReservationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        eventPublisher.publish(KafkaTopics.INVENTORY_EVENTS, orderId.toString(), new InventoryReservedEvent(
                event.getCorrelationId(), orderId, event.getCustomerId(), event.getTotalAmount()));
        log.info("Inventory reserved for order: {}", orderId);
    }

    @Override
    @Transactional
    public void confirmReservation(BaseEvent cause, UUID orderId) {
        if (!idempotencyGuard.firstDelivery(cause)) {
            return;
        }
        Optional<InventoryReservation> pending = pendingReservation(orderId);
        if (pending.isEmpty()) {
            return;
        }
        InventoryReservation reservation = pending.get();
        for (var reservationItem : reservation.getItems()) {
            InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
            item.setReservedQuantity(item.getReservedQuantity() - reservationItem.getQuantity());
            inventoryRepository.save(item);
        }
        updateStatus(reservation, ReservationStatus.CONFIRMED);
        log.info("Reservation confirmed for order: {}", orderId);
    }

    @Override
    @Transactional
    public void releaseReservation(BaseEvent cause, UUID orderId) {
        if (!idempotencyGuard.firstDelivery(cause)) {
            return;
        }
        Optional<InventoryReservation> pending = pendingReservation(orderId);
        if (pending.isEmpty()) {
            return;
        }
        InventoryReservation reservation = pending.get();
        for (var reservationItem : reservation.getItems()) {
            InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
            item.setAvailableQuantity(item.getAvailableQuantity() + reservationItem.getQuantity());
            item.setReservedQuantity(item.getReservedQuantity() - reservationItem.getQuantity());
            inventoryRepository.save(item);
        }
        updateStatus(reservation, ReservationStatus.CANCELLED);
        log.info("Reservation released for order: {}", orderId);
    }

    /** Only a PENDING reservation can be confirmed or released; anything else is already settled. */
    private Optional<InventoryReservation> pendingReservation(UUID orderId) {
        Optional<InventoryReservation> reservation = reservationRepository.findByOrderId(orderId);
        if (reservation.isEmpty()) {
            log.warn("No reservation for order {}", orderId);
            return Optional.empty();
        }
        if (reservation.get().getStatus() != ReservationStatus.PENDING) {
            log.info("Reservation for order {} already {}", orderId, reservation.get().getStatus());
            return Optional.empty();
        }
        return reservation;
    }

    private void updateStatus(InventoryReservation reservation, ReservationStatus status) {
        reservation.setStatus(status);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
    }
}
