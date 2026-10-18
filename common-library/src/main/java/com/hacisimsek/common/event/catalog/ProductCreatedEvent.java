package com.hacisimsek.common.event.catalog;

import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class ProductCreatedEvent extends BaseEvent {
    private UUID productId;
    private String sku;
    private String name;
    private BigDecimal price;

    public ProductCreatedEvent(UUID correlationId, UUID productId, String sku, String name, BigDecimal price) {
        super(correlationId);
        this.productId = productId;
        this.sku = sku;
        this.name = name;
        this.price = price;
    }
}
