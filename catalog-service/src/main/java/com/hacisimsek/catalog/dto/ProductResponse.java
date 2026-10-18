package com.hacisimsek.catalog.dto;

import com.hacisimsek.catalog.model.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id, String sku, String name, String description, BigDecimal price,
                              String imageUrl, UUID categoryId, String categoryName, boolean active) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getPrice(), product.getImageUrl(), product.getCategory().getId(),
                product.getCategory().getName(), product.isActive());
    }
}
