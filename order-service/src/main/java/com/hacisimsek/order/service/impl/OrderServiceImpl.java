package com.hacisimsek.order.service.impl;

import com.hacisimsek.common.client.catalog.CatalogClient;
import com.hacisimsek.common.client.catalog.CatalogProduct;
import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.BaseEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.common.messaging.EventPublisher;
import com.hacisimsek.common.messaging.IdempotencyGuard;
import com.hacisimsek.common.web.BusinessRuleException;
import com.hacisimsek.common.web.ResourceNotFoundException;
import com.hacisimsek.order.dto.OrderCreation;
import com.hacisimsek.order.dto.OrderItemRequest;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final IdempotencyGuard idempotencyGuard;
    private final CatalogClient catalogClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    public OrderCreation createOrder(OrderRequest orderRequest, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay of order {} (key {})", existing.get().getId(), idempotencyKey);
                return new OrderCreation(mapToOrderResponse(existing.get()), true);
            }
        }

        // Remote call outside the transaction: no database connection is held while waiting on the catalog
        List<OrderItem> orderItems = priceItems(orderRequest.getItems());

        Order savedOrder = transactionTemplate.execute(status -> persistAndStartSaga(
                orderRequest.getCustomerId(), orderItems, idempotencyKey));
        return new OrderCreation(mapToOrderResponse(savedOrder), false);
    }

    /** Merges repeated products and takes name and unit price from the catalog. */
    private List<OrderItem> priceItems(List<OrderItemRequest> requested) {
        Map<UUID, Integer> quantities = requested.stream()
                .collect(Collectors.toMap(OrderItemRequest::getProductId, OrderItemRequest::getQuantity,
                        Integer::sum, LinkedHashMap::new));
        Map<UUID, CatalogProduct> products = catalogClient.findProducts(quantities.keySet());

        List<String> unavailable = quantities.keySet().stream()
                .filter(id -> products.get(id) == null || !products.get(id).active())
                .map(UUID::toString)
                .toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessRuleException("Products not available: " + String.join(", ", unavailable));
        }

        return quantities.entrySet().stream()
                .map(e -> {
                    CatalogProduct product = products.get(e.getKey());
                    return OrderItem.builder()
                            .productId(product.id())
                            .productName(product.name())
                            .quantity(e.getValue())
                            .price(product.price())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Order persistAndStartSaga(UUID customerId, List<OrderItem> orderItems, String idempotencyKey) {
        BigDecimal totalAmount = orderItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order savedOrder = orderRepository.save(Order.builder()
                .customerId(customerId)
                .totalAmount(totalAmount)
                // The saga starts as soon as the order is committed
                .status(Order.OrderStatus.INVENTORY_CHECKING)
                .items(orderItems)
                .idempotencyKey(idempotencyKey)
                .build());

        List<OrderItemDto> itemDtos = savedOrder.getItems().stream()
                .map(item -> new OrderItemDto(item.getProductId(), item.getQuantity(), item.getPrice()))
                .collect(Collectors.toList());
        OrderCreatedEvent event = new OrderCreatedEvent(UUID.randomUUID(), savedOrder.getId(),
                savedOrder.getCustomerId(), itemDtos, savedOrder.getTotalAmount());

        // Written to the outbox in this transaction; the relay delivers it to Kafka after commit
        eventPublisher.publish(KafkaTopics.ORDER_EVENTS, savedOrder.getId().toString(), event);
        log.info("Created order {} for customer {} totalling {}", savedOrder.getId(), customerId, totalAmount);
        return savedOrder;
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
