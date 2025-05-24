package com.hacisimsek.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemDto {
    private UUID productId;
    private Integer quantity;
    private BigDecimal price;
}