package com.hacisimsek.order.service.impl;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.order.dto.OrderItemResponse;
import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.model.OrderItem;
import com.hacisimsek.order.repository.OrderRepository;
import com.hacisimsek.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;

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
                .status(Order.OrderStatus.PENDING)
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

        log.info("Sending OrderCreatedEvent for order {}", savedOrder.getId());

        // Update order status to indicate saga started
        savedOrder.setStatus(Order.OrderStatus.INVENTORY_CHECKING);
        orderRepository.save(savedOrder);

        // Publish event to Kafka
        kafkaTemplate.send("order-events", event);

        return mapToOrderResponse(savedOrder);
    }

    @Override
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        return mapToOrderResponse(order);
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
        log.info("Updated order {} status to {}", orderId, status);
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