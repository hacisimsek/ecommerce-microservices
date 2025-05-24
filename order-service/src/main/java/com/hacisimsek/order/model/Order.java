package com.hacisimsek.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID customerId;
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastModifiedAt;
    private String lastModifiedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.parse("2025-05-24T13:39:46");
        this.lastModifiedAt = LocalDateTime.parse("2025-05-24T13:39:46");
        this.createdBy = "simsekhaciI";
        this.lastModifiedBy = "simsekhaciI";
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
        this.lastModifiedBy = "simsekhaciI";
    }

    public enum OrderStatus {
        PENDING,
        INVENTORY_CHECKING,
        INVENTORY_RESERVED,
        PAYMENT_PROCESSING,
        PAYMENT_COMPLETED,
        SHIPPING_PROCESSING,
        SHIPPED,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}