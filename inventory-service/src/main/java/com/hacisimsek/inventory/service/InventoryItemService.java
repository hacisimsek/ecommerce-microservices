package com.hacisimsek.inventory.service;

import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.model.InventoryItem;

import java.util.List;
import java.util.UUID;

public interface InventoryItemService {
    InventoryItem createInventoryItem(InventoryItemRequest request);
    InventoryItem getInventoryItemById(UUID id);
    List<InventoryItem> getAllInventoryItems();
    InventoryItem updateInventoryItem(UUID id, InventoryItemRequest request);
    void deleteInventoryItem(UUID id);
    boolean checkAvailability(UUID productId, Integer quantity);
}