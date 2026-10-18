package com.hacisimsek.catalog.repository;

import com.hacisimsek.catalog.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySku(String sku);

    /** Active products, optionally filtered by category and a case-insensitive name/description match. */
    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:query IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))""")
    Page<Product> search(@Param("categoryId") UUID categoryId, @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    List<Product> findByIdIn(Collection<UUID> ids);
}
