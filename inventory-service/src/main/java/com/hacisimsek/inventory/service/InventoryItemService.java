package com.hacisimsek.inventory.service;

import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.model.InventoryItem;

import java.util.List;
// import java.util.UUID;

public interface InventoryItemService {
    InventoryItem createInventoryItem(InventoryItemRequest request);
    InventoryItem getInventoryItemById(String id);
    List<InventoryItem> getAllInventoryItems();
    InventoryItem updateInventoryItem(String id, InventoryItemRequest request);
    void deleteInventoryItem(String id);
    boolean checkAvailability(String productId, Integer quantity);
}