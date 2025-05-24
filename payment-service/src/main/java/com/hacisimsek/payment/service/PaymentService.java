package com.hacisimsek.payment.service;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.payment.model.Payment;

import java.util.UUID;

public interface PaymentService {
    void processPayment(InventoryReservedEvent event);
    Payment getPaymentByOrderId(UUID orderId);
}