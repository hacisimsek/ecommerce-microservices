package com.hacisimsek.common.event.inventory;

import com.hacisimsek.common.event.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
public class InventoryReservedEvent extends BaseEvent {
    private UUID orderId;

    public InventoryReservedEvent(UUID correlationId, UUID orderId) {
        super(correlationId);
        this.orderId = orderId;
    }
}