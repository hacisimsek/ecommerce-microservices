package com.hacisimsek.inventory.service.impl;

import com.hacisimsek.common.event.catalog.ProductCreatedEvent;
import com.hacisimsek.common.event.catalog.ProductUpdatedEvent;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.common.web.ResourceNotFoundException;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.service.InventoryItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryItemServiceImpl implements InventoryItemService {

    private final InventoryRepository inventoryRepository;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public void createFromCatalog(ProductCreatedEvent event) {
        if (!idempotencyGuard.firstDelivery(event) || inventoryRepository.existsById(event.getProductId())) {
            return;
        }
        inventoryRepository.save(InventoryItem.builder()
                .id(event.getProductId())
                .sku(event.getSku())
                .name(event.getName())
                .availableQuantity(0)
                .reservedQuantity(0)
                .build());
        log.info("Created stock record for product {} ({})", event.getSku(), event.getProductId());
    }

    @Override
    @Transactional
    public void updateFromCatalog(ProductUpdatedEvent event) {
        if (!idempotencyGuard.firstDelivery(event)) {
            return;
        }
        inventoryRepository.findById(event.getProductId()).ifPresent(item -> {
            item.setSku(event.getSku());
            item.setName(event.getName());
            inventoryRepository.save(item);
        });
    }

    @Override
    public InventoryItem getInventoryItemById(UUID id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", id));
    }

    @Override
    public List<InventoryItem> getAllInventoryItems() {
        return inventoryRepository.findAll();
    }

    @Override
    @Transactional
    public InventoryItem setAvailableQuantity(UUID id, int availableQuantity) {
        InventoryItem item = getInventoryItemById(id);
        item.setAvailableQuantity(availableQuantity);
        log.info("Set available stock of {} to {}", id, availableQuantity);
        return inventoryRepository.save(item);
    }

    @Override
    public boolean checkAvailability(UUID productId, Integer quantity) {
        return inventoryRepository.findById(productId)
                .map(item -> item.getAvailableQuantity() >= quantity)
                .orElse(false);
    }
}
