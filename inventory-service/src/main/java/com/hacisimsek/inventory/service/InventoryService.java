package com.hacisimsek.inventory.service;

import com.hacisimsek.common.event.order.OrderCreatedEvent;

import java.util.UUID;

public interface InventoryService {
    void reserveInventory(OrderCreatedEvent orderCreatedEvent);
    void confirmReservation(String orderId);
    void cancelReservation(String orderId);
}