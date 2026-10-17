package com.hacisimsek.catalog.controller;

import com.hacisimsek.catalog.dto.CategoryRequest;
import com.hacisimsek.catalog.dto.CategoryResponse;
import com.hacisimsek.catalog.dto.PageResponse;
import com.hacisimsek.catalog.dto.ProductRequest;
import com.hacisimsek.catalog.dto.ProductResponse;
import com.hacisimsek.catalog.service.CatalogService;
import com.hacisimsek.common.client.catalog.CatalogProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return catalogService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    /** Browse active products: {@code ?categoryId=&q=&page=0&size=20&sort=price,asc}. */
    @GetMapping("/products")
    public PageResponse<ProductResponse> searchProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(name = "q", required = false) String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return catalogService.searchProducts(categoryId, query, pageable);
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable UUID id) {
        return catalogService.getProduct(id);
    }

    /** Batch lookup for other services: {@code ?ids=a,b,c}. */
    @GetMapping("/products/lookup")
    public List<CatalogProduct> lookup(@RequestParam @Size(min = 1, max = 100) List<UUID> ids) {
        return catalogService.lookup(ids);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return catalogService.updateProduct(id, request);
    }
}
