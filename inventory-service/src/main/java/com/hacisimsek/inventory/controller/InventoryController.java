package com.hacisimsek.inventory.controller;

import com.hacisimsek.inventory.dto.InventoryItemResponse;
import com.hacisimsek.inventory.dto.StockUpdateRequest;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.service.InventoryItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Stock per product. Records are created from catalog events, so the id is the product id. */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryItemService inventoryItemService;

    @GetMapping
    public List<InventoryItemResponse> getAllInventoryItems() {
        return inventoryItemService.getAllInventoryItems().stream().map(this::mapToResponse).toList();
    }

    @GetMapping("/{productId}")
    public InventoryItemResponse getInventoryItem(@PathVariable UUID productId) {
        return mapToResponse(inventoryItemService.getInventoryItemById(productId));
    }

    @PutMapping("/{productId}/stock")
    public InventoryItemResponse setStock(@PathVariable UUID productId, @Valid @RequestBody StockUpdateRequest request) {
        return mapToResponse(inventoryItemService.setAvailableQuantity(productId, request.availableQuantity()));
    }

    @GetMapping("/check")
    public boolean checkInventoryAvailability(@RequestParam UUID productId, @RequestParam Integer quantity) {
        return inventoryItemService.checkAvailability(productId, quantity);
    }

    private InventoryItemResponse mapToResponse(InventoryItem item) {
        return InventoryItemResponse.builder()
                .id(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription())
                .availableQuantity(item.getAvailableQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .build();
    }
}
