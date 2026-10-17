package com.hacisimsek.order.service.impl;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.common.messaging.EventPublisher;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.common.web.ResourceNotFoundException;
import com.hacisimsek.order.dto.OrderItemResponse;
import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.model.OrderItem;
import com.hacisimsek.order.repository.OrderRepository;
import com.hacisimsek.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest orderRequest) {
        // Convert order items from request
        List<OrderItem> orderItems = orderRequest.getItems().stream()
                .map(item -> OrderItem.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        // Calculate total amount
        BigDecimal totalAmount = orderItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Create and save order
        Order order = Order.builder()
                .customerId(orderRequest.getCustomerId())
                .totalAmount(totalAmount)
                // The saga starts as soon as the order is committed
                .status(Order.OrderStatus.INVENTORY_CHECKING)
                .items(orderItems)
                .build();

        Order savedOrder = orderRepository.save(order);

        // Start the saga by sending OrderCreatedEvent
        UUID correlationId = UUID.randomUUID();
        List<OrderItemDto> itemDtos = savedOrder.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice()))
                .collect(Collectors.toList());

        OrderCreatedEvent event = new OrderCreatedEvent(
                correlationId,
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                itemDtos,
                savedOrder.getTotalAmount()
        );

        // Written to the outbox in this transaction; the relay delivers it to Kafka after commit
        eventPublisher.publish(KafkaTopics.ORDER_EVENTS, savedOrder.getId().toString(), event);

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateOrderStatus(BaseEvent cause, UUID orderId, Order.OrderStatus status) {
        if (!idempotencyGuard.firstDelivery(cause)) {
            return;
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!order.getStatus().canTransitionTo(status)) {
            log.warn("Ignoring status change {} -> {} for order {}", order.getStatus(), status, orderId);
            return;
        }
        log.info("Updating order {} status {} -> {}", orderId, order.getStatus(), status);
        order.setStatus(status);
        orderRepository.save(order);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .lastModifiedAt(order.getLastModifiedAt())
                .build();
    }
}
