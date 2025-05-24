package com.hacisimsek.inventory.repository;

import com.hacisimsek.inventory.model.InventoryItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface InventoryRepository extends MongoRepository<InventoryItem, UUID> {
}