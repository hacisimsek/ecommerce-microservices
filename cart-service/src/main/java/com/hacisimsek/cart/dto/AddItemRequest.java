package com.hacisimsek.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddItemRequest(@NotNull UUID productId, @NotNull @Min(1) Integer quantity) {
}
