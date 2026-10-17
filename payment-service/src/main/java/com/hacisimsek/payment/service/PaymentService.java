package com.hacisimsek.payment.service;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.payment.model.Payment;

import java.util.UUID;

public interface PaymentService {

    void processPayment(InventoryReservedEvent event);

    /** Compensation: refunds a completed payment when the order cannot be shipped. */
    void refundPayment(ShipmentFailedEvent event);

    Payment getPaymentByOrderId(UUID orderId);
}
