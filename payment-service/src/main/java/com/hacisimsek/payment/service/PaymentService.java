package com.hacisimsek.payment.service;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.payment.model.Payment;

import java.util.UUID;
// dummy commet for triger pipeline
public interface PaymentService {
    void processPayment(InventoryReservedEvent event);
    Payment getPaymentByOrderId(UUID orderId);
}