package com.hacisimsek.common.event.payment;

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
public class PaymentRefundedEvent extends BaseEvent {
    private UUID orderId;
    private UUID customerId;
    private UUID paymentId;
    private BigDecimal amount;

    public PaymentRefundedEvent(UUID correlationId, UUID orderId, UUID customerId, UUID paymentId, BigDecimal amount) {
        super(correlationId);
        this.orderId = orderId;
        this.customerId = customerId;
        this.paymentId = paymentId;
        this.amount = amount;
    }
}
