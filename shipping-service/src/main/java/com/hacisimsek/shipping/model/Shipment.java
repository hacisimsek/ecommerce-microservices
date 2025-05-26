package com.hacisimsek.shipping.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID orderId;
    private UUID customerId;
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    private String trackingNumber;
    private String carrierName;
    private LocalDateTime shippedDate;
    private LocalDateTime estimatedDeliveryDate;

    private String shippingAddress;
    private String recipientName;
    private String recipientPhone;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime lastModifiedAt;
    private String lastModifiedBy;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.parse("2025-05-24T16:33:57");
        this.lastModifiedAt = LocalDateTime.parse("2025-05-24T16:33:57");
        this.createdBy = "simsekhaci";
        this.lastModifiedBy = "simsekhaci";
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
        this.lastModifiedBy = "simsekhaci";
    }

    public enum ShipmentStatus {
        PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    }
}