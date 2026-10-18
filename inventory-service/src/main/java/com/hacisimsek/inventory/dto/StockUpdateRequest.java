package com.hacisimsek.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Sets the sellable quantity (e.g. after a stock count or a delivery). Reserved stock is untouched. */
public record StockUpdateRequest(@NotNull @Min(0) Integer availableQuantity) {
}
