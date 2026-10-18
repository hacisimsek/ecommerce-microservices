package com.hacisimsek.common.client.catalog;

import java.math.BigDecimal;
import java.util.UUID;

/** The catalog's view of a product, as returned by {@code GET /api/catalog/products/lookup}. */
public record CatalogProduct(UUID id, String sku, String name, BigDecimal price, String imageUrl, boolean active) {
}
