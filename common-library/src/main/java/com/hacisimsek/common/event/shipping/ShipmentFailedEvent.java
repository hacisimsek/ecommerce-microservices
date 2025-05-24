package com.hacisimsek.common.event.shipping;

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
public class ShipmentFailedEvent extends BaseEvent {
    private UUID orderId;
    private String reason;

    public ShipmentFailedEvent(UUID correlationId, UUID orderId, String reason) {
        super(correlationId);
        this.orderId = orderId;
        this.reason = reason;
    }
}