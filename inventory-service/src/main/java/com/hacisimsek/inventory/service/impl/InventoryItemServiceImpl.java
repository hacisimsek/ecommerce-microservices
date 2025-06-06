package com.hacisimsek.inventory.service.impl;

import com.hacisimsek.inventory.dto.InventoryItemRequest;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.service.InventoryItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryItemServiceImpl implements InventoryItemService {

    private final InventoryRepository inventoryRepository;
    private final String username = "simsekhaci";
    private final LocalDateTime timestamp = LocalDateTime.parse("2025-05-24T16:45:26", DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    @Override
    @Transactional
    public InventoryItem createInventoryItem(InventoryItemRequest request) {
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .description(request.getDescription())
                .availableQuantity(request.getQuantity())
                .reservedQuantity(0)
                .build();

        log.info("Creating new inventory item: {}", item.getName());
        return inventoryRepository.save(item);
    }

    @Override
    public InventoryItem getInventoryItemById(UUID id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory item not found with id: " + id));
    }

    @Override
    public List<InventoryItem> getAllInventoryItems() {
        return inventoryRepository.findAll();
    }

    @Override
    @Transactional
    public InventoryItem updateInventoryItem(UUID id, InventoryItemRequest request) {
        InventoryItem existingItem = getInventoryItemById(id);

        existingItem.setName(request.getName());
        existingItem.setDescription(request.getDescription());
        existingItem.setAvailableQuantity(request.getQuantity());

        log.info("Updating inventory item: {}", existingItem.getId());
        return inventoryRepository.save(existingItem);
    }

    @Override
    @Transactional
    public void deleteInventoryItem(UUID id) {
        InventoryItem item = getInventoryItemById(id);
        log.info("Deleting inventory item: {}", id);
        inventoryRepository.delete(item);
    }

    @Override
    public boolean checkAvailability(UUID productId, Integer quantity) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElse(null);

        if (item == null) {
            return false;
        }

        return item.getAvailableQuantity() >= quantity;
    }
}
