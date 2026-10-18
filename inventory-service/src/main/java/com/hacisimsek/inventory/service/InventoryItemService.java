package com.hacisimsek.inventory.service;

import com.hacisimsek.common.event.catalog.ProductCreatedEvent;
import com.hacisimsek.common.event.catalog.ProductUpdatedEvent;
import com.hacisimsek.inventory.model.InventoryItem;

import java.util.List;
import java.util.UUID;

public interface InventoryItemService {

    /** Every catalog product gets a stock record (id = product id) starting at zero. */
    void createFromCatalog(ProductCreatedEvent event);

    void updateFromCatalog(ProductUpdatedEvent event);

    InventoryItem getInventoryItemById(UUID id);

    List<InventoryItem> getAllInventoryItems();

    InventoryItem setAvailableQuantity(UUID id, int availableQuantity);

    boolean checkAvailability(UUID productId, Integer quantity);
}
