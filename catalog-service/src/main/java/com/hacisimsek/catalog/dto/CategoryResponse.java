package com.hacisimsek.catalog.dto;

import com.hacisimsek.catalog.model.Category;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String slug, String description) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getDescription());
    }
}
