package com.hacisimsek.order.dto;

/** @param replayed true when an earlier request with the same idempotency key already created the order */
public record OrderCreation(OrderResponse order, boolean replayed) {
}
