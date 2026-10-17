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

    private static final String SYSTEM_USER = "system";
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID customerId;
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastModifiedAt;
    private String lastModifiedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = this.createdAt;
        this.createdBy = SYSTEM_USER;
        this.lastModifiedBy = SYSTEM_USER;
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
        this.lastModifiedBy = SYSTEM_USER;
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
        FAILED;

        public boolean isTerminal() {
            return this == COMPLETED || this == CANCELLED || this == FAILED;
        }

        /**
         * Saga events arrive on different topics, so they can be consumed out of order.
         * Only forward moves (or a move to a failure state) are accepted, and terminal states are final.
         */
        public boolean canTransitionTo(OrderStatus next) {
            if (isTerminal()) {
                return false;
            }
            if (next == CANCELLED || next == FAILED) {
                return true;
            }
            return next.ordinal() > ordinal();
        }
    }
}
