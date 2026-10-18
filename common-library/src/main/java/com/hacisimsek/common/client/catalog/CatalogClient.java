package com.hacisimsek.common.client.catalog;

import com.hacisimsek.common.web.DependencyUnavailableException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Synchronous product lookup against catalog-service. Calls go through a circuit breaker with a
 * time limit, so a slow or failing catalog turns into a fast {@link DependencyUnavailableException}
 * (HTTP 503) instead of piling up blocked requests.
 */
public class CatalogClient {

    static final String DEPENDENCY = "catalog-service";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public CatalogClient(RestClient restClient, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
    }

    /** Products by id; ids the catalog does not know are simply absent from the result. */
    public Map<UUID, CatalogProduct> findProducts(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<CatalogProduct> products = circuitBreaker.run(() -> fetch(ids), ex -> {
            throw new DependencyUnavailableException(DEPENDENCY, ex);
        });
        return products.stream().collect(Collectors.toMap(CatalogProduct::id, Function.identity()));
    }

    private List<CatalogProduct> fetch(Collection<UUID> ids) {
        return restClient.get()
                .uri(uri -> uri.path("/api/catalog/products/lookup")
                        .queryParam("ids", ids.stream().map(UUID::toString).collect(Collectors.joining(",")))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }
}
