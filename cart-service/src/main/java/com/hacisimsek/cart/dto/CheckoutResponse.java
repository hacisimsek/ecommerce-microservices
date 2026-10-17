package com.hacisimsek.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** The order created from the cart; follow its progress at {@code GET /api/orders/{orderId}}. */
public record CheckoutResponse(UUID orderId, String status, BigDecimal totalAmount) {
}
