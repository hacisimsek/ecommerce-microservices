package com.hacisimsek.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Clients only say what and how many; name and price always come from the catalog. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemRequest {
    @NotNull
    private UUID productId;

    @NotNull
    @Min(1)
    @Max(99)
    private Integer quantity;
}
