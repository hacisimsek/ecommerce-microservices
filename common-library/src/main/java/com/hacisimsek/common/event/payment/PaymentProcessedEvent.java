package com.hacisimsek.common.event.payment;

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
public class PaymentProcessedEvent extends BaseEvent {
    private UUID orderId;
    private UUID paymentId;

    public PaymentProcessedEvent(UUID correlationId, UUID orderId, UUID paymentId) {
        super(correlationId);
        this.orderId = orderId;
        this.paymentId = paymentId;
    }
}