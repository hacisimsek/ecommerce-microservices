package com.hacisimsek.catalog.service;

import com.hacisimsek.catalog.dto.CategoryRequest;
import com.hacisimsek.catalog.dto.CategoryResponse;
import com.hacisimsek.catalog.dto.PageResponse;
import com.hacisimsek.catalog.dto.ProductRequest;
import com.hacisimsek.catalog.dto.ProductResponse;
import com.hacisimsek.catalog.model.Category;
import com.hacisimsek.catalog.model.Product;
import com.hacisimsek.catalog.repository.CategoryRepository;
import com.hacisimsek.catalog.repository.ProductRepository;
import com.hacisimsek.common.client.catalog.CatalogProduct;
import com.hacisimsek.common.event.catalog.ProductCreatedEvent;
import com.hacisimsek.common.event.catalog.ProductUpdatedEvent;
import com.hacisimsek.common.kafka.KafkaTopics;
import com.hacisimsek.common.messaging.EventPublisher;
import com.hacisimsek.common.web.BusinessRuleException;
import com.hacisimsek.common.web.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final EventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new BusinessRuleException("Category slug already exists: " + request.slug());
        }
        Category category = categoryRepository.save(Category.builder()
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .build());
        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(UUID categoryId, String query, Pageable pageable) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        return PageResponse.from(productRepository.search(categoryId, normalizedQuery, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return ProductResponse.from(findProduct(id));
    }

    /** Used by order and cart services; includes inactive products so callers can reject them explicitly. */
    @Transactional(readOnly = true)
    public List<CatalogProduct> lookup(Collection<UUID> ids) {
        return productRepository.findByIdIn(ids).stream()
                .map(p -> new CatalogProduct(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getImageUrl(),
                        p.isActive()))
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new BusinessRuleException("SKU already exists: " + request.sku());
        }
        Product product = productRepository.save(Product.builder()
                .sku(request.sku())
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .category(findCategory(request.categoryId()))
                .active(request.active() == null || request.active())
                .build());

        // Inventory creates the matching stock record from this event
        eventPublisher.publish(KafkaTopics.CATALOG_EVENTS, product.getId().toString(), new ProductCreatedEvent(
                UUID.randomUUID(), product.getId(), product.getSku(), product.getName(), product.getPrice()));
        log.info("Created product {} ({})", product.getSku(), product.getId());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, ProductRequest request) {
        Product product = findProduct(id);
        if (!product.getSku().equals(request.sku()) && productRepository.existsBySku(request.sku())) {
            throw new BusinessRuleException("SKU already exists: " + request.sku());
        }
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setImageUrl(request.imageUrl());
        product.setCategory(findCategory(request.categoryId()));
        if (request.active() != null) {
            product.setActive(request.active());
        }
        productRepository.save(product);

        eventPublisher.publish(KafkaTopics.CATALOG_EVENTS, product.getId().toString(), new ProductUpdatedEvent(
                UUID.randomUUID(), product.getId(), product.getSku(), product.getName(), product.getPrice(),
                product.isActive()));
        return ProductResponse.from(product);
    }

    private Product findProduct(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Unknown category: " + id));
    }
}
