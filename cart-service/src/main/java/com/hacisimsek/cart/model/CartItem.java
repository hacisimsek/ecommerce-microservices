package com.hacisimsek.cart.model;

import java.math.BigDecimal;
import java.util.UUID;

/** One cart line. Name, price and image are a snapshot from the catalog, refreshed on every read. */
public record CartItem(UUID productId, String sku, String name, BigDecimal unitPrice, String imageUrl, int quantity) {

    public CartItem withQuantity(int newQuantity) {
        return new CartItem(productId, sku, name, unitPrice, imageUrl, newQuantity);
    }
}
