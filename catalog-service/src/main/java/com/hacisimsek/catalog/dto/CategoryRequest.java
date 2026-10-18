package com.hacisimsek.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CategoryRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "must be lowercase words separated by '-'")
        String slug,
        String description) {
}
