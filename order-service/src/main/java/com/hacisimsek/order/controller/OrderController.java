package com.hacisimsek.order.controller;

import com.hacisimsek.order.dto.OrderCreation;
import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates an order priced from the catalog. With an {@code Idempotency-Key} header a retried
     * request returns the original order (200) instead of creating a second one (201).
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest orderRequest,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 100) String idempotencyKey) {
        OrderCreation creation = orderService.createOrder(orderRequest, idempotencyKey);
        return ResponseEntity.status(creation.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(creation.order());
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(@PathVariable UUID orderId) {
        return orderService.getOrderById(orderId);
    }

    @GetMapping
    public List<OrderResponse> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/customer/{customerId}")
    public List<OrderResponse> getOrdersByCustomerId(@PathVariable UUID customerId) {
        return orderService.getOrdersByCustomerId(customerId);
    }
}
