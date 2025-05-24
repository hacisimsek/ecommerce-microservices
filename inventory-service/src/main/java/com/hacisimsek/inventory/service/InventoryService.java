package com.hacisimsek.inventory.service;

import com.hacisimsek.common.event.order.OrderCreatedEvent;

import java.util.UUID;

public interface InventoryService {
    void reserveInventory(OrderCreatedEvent orderCreatedEvent);
    void confirmReservation(UUID orderId);
    void cancelReservation(UUID orderId);
}