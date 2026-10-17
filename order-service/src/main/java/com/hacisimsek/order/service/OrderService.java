package com.hacisimsek.order.service;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.order.dto.OrderCreation;
import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.model.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    /**
     * Prices the order from the catalog and starts the saga.
     *
     * @param idempotencyKey optional; a repeated key returns the existing order instead of a new one
     */
    OrderCreation createOrder(OrderRequest orderRequest, String idempotencyKey);
    OrderResponse getOrderById(UUID orderId);
    List<OrderResponse> getAllOrders();
    List<OrderResponse> getOrdersByCustomerId(UUID customerId);

    /** Applies the status change a saga event implies; duplicate deliveries are ignored. */
    void updateOrderStatus(BaseEvent cause, UUID orderId, Order.OrderStatus status);
}
