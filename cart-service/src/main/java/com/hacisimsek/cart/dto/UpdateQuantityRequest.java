package com.hacisimsek.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Quantity 0 removes the line. */
public record UpdateQuantityRequest(@NotNull @Min(0) Integer quantity) {
}
