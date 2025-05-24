package com.hacisimsek.inventory.service.impl;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryReservation;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.repository.ReservationRepository;
import com.hacisimsek.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
        log.info("Processing inventory reservation for order: {}", orderCreatedEvent.getOrderId());

        List<InventoryReservation.ReservationItem> reservationItems = new ArrayList<>();
        boolean allItemsAvailable = true;
        StringBuilder insufficientItemsMessage = new StringBuilder();

        // Check if all items are available in sufficient quantity
        for (var orderItem : orderCreatedEvent.getItems()) {
            InventoryItem item = inventoryRepository.findById(orderItem.getProductId())
                    .orElse(null);

            if (item == null) {
                allItemsAvailable = false;
                insufficientItemsMessage.append("Product not found: ")
                        .append(orderItem.getProductId()).append("; ");
                continue;
            }

            if (item.getAvailableQuantity() < orderItem.getQuantity()) {
                allItemsAvailable = false;
                insufficientItemsMessage.append("Insufficient quantity for product: ")
                        .append(orderItem.getProductId())
                        .append(", requested: ").append(orderItem.getQuantity())
                        .append(", available: ").append(item.getAvailableQuantity())
                        .append("; ");
                continue;
            }

            // Add to reservation items
            reservationItems.add(InventoryReservation.ReservationItem.builder()
                    .productId(orderItem.getProductId())
                    .quantity(orderItem.getQuantity())
                    .build());
        }

        if (!allItemsAvailable) {
            // Send failure event
            InventoryReservationFailedEvent failedEvent = new InventoryReservationFailedEvent(
                    orderCreatedEvent.getCorrelationId(),
                    orderCreatedEvent.getOrderId(),
                    insufficientItemsMessage.toString()
            );

            kafkaTemplate.send("inventory-events", failedEvent);
            log.error("Inventory reservation failed: {}", insufficientItemsMessage);
            return;
        }

        // Create reservation
        InventoryReservation reservation = InventoryReservation.builder()
                .id(UUID.randomUUID())
                .orderId(orderCreatedEvent.getOrderId())
                .correlationId(orderCreatedEvent.getCorrelationId())
                .items(reservationItems)
                .status(InventoryReservation.ReservationStatus.CONFIRMED)
                .createdAt(LocalDateTime.parse("2025-05-24T13:39:46"))
                .updatedAt(LocalDateTime.parse("2025-05-24T13:39:46"))
                .build();

        reservationRepository.save(reservation);

        // Update inventory quantities
        for (var reservationItem : reservationItems) {
            InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
            item.setAvailableQuantity(item.getAvailableQuantity() - reservationItem.getQuantity());
            item.setReservedQuantity(item.getReservedQuantity() + reservationItem.getQuantity());
            inventoryRepository.save(item);
        }

        // Send success event
        InventoryReservedEvent reservedEvent = new InventoryReservedEvent(
                orderCreatedEvent.getCorrelationId(),
                orderCreatedEvent.getOrderId()
        );

        kafkaTemplate.send("inventory-events", reservedEvent);
        log.info("Inventory successfully reserved for order: {}", orderCreatedEvent.getOrderId());
    }

    @Override
    @Transactional
    public void confirmReservation(UUID orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for order: " + orderId));

        reservation.setStatus(InventoryReservation.ReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Reservation confirmed for order: {}", orderId);
    }

    @Override
    @Transactional
    public void cancelReservation(UUID orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for order: " + orderId));

        if (reservation.getStatus() == InventoryReservation.ReservationStatus.CANCELLED) {
            log.info("Reservation for order {} already cancelled", orderId);
            return;
        }

        // Return quantities back to available inventory
        for (var reservationItem : reservation.getItems()) {
            InventoryItem item = inventoryRepository.findById(reservationItem.getProductId()).orElseThrow();
            item.setAvailableQuantity(item.getAvailableQuantity() + reservationItem.getQuantity());
            item.setReservedQuantity(item.getReservedQuantity() - reservationItem.getQuantity());
            inventoryRepository.save(item);
        }

        reservation.setStatus(InventoryReservation.ReservationStatus.CANCELLED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        log.info("Reservation cancelled and inventory released for order: {}", orderId);
    }
}