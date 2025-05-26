package com.hacisimsek.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "inventory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {
    @Id
    private UUID id;
    private String name;
    private String description;
    private Integer availableQuantity;
    private Integer reservedQuantity;

    @CreatedDate
    private LocalDateTime createdAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // Manually setting these since we're not using MongoAuditing in this example
    @Builder.Default
    private LocalDateTime creationTimestamp = LocalDateTime.parse("2025-05-26T17:49:38");

    @Builder.Default
    private String creator = "simsekhacitekrar";
}