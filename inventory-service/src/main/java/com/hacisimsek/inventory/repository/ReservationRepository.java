package com.hacisimsek.inventory.repository;

import com.hacisimsek.inventory.model.InventoryReservation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends MongoRepository<InventoryReservation, String> {
    Optional<InventoryReservation> findByOrderId(String orderId);
}