package com.hacisimsek.cart.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A customer's cart as stored in Redis. {@code revision} changes on every modification and is
 * sent as the order's idempotency key, so retrying a checkout never creates a second order.
 */
public record Cart(UUID customerId, List<CartItem> items, UUID revision, Instant updatedAt) {

    public static Cart empty(UUID customerId) {
        return new Cart(customerId, List.of(), UUID.randomUUID(), Instant.now());
    }

    public Optional<CartItem> item(UUID productId) {
        return items.stream().filter(i -> i.productId().equals(productId)).findFirst();
    }

    /** Replaces (or adds) the line for the item's product; quantity 0 removes it. */
    public Cart withItem(CartItem item) {
        List<CartItem> updated = new ArrayList<>(items.stream()
                .filter(i -> !i.productId().equals(item.productId()))
                .toList());
        if (item.quantity() > 0) {
            updated.add(item);
        }
        return new Cart(customerId, List.copyOf(updated), UUID.randomUUID(), Instant.now());
    }

    public Cart withoutItem(UUID productId) {
        return new Cart(customerId, items.stream().filter(i -> !i.productId().equals(productId)).toList(),
                UUID.randomUUID(), Instant.now());
    }
}
