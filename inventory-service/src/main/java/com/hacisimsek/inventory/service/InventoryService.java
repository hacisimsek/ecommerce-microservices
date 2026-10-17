package com.hacisimsek.inventory.service;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;

import java.util.UUID;

/** Saga steps owned by the inventory service. Every method ignores duplicate deliveries. */
public interface InventoryService {

    /** Moves stock from available to reserved, or reports why it cannot. */
    void reserveInventory(OrderCreatedEvent event);

    /** The order shipped: reserved stock has left the warehouse. */
    void confirmReservation(BaseEvent cause, UUID orderId);

    /** Compensation: puts reserved stock back to available. */
    void releaseReservation(BaseEvent cause, UUID orderId);
}
