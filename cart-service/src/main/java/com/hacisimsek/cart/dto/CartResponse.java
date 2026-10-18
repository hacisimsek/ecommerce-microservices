package com.hacisimsek.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param pricesStale true when the catalog could not be reached and the prices shown are the
 *                    last known ones; checkout always uses current catalog prices
 */
public record CartResponse(UUID customerId, List<Line> items, int itemCount, BigDecimal subtotal,
                           boolean pricesStale, Instant updatedAt) {

    /** @param available false when the product was deactivated; checkout will reject it */
    public record Line(UUID productId, String sku, String name, BigDecimal unitPrice, String imageUrl,
                       int quantity, BigDecimal lineTotal, boolean available) {
    }
}
