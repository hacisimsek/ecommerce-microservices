package com.hacisimsek.order.service;

import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.model.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse createOrder(OrderRequest orderRequest);
    OrderResponse getOrderById(UUID orderId);
    List<OrderResponse> getAllOrders();
    List<OrderResponse> getOrdersByCustomerId(UUID customerId);

    /** Applies the status change a saga event implies; duplicate deliveries are ignored. */
    void updateOrderStatus(BaseEvent cause, UUID orderId, Order.OrderStatus status);
}
