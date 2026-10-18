package com.hacisimsek.cart.client;

import com.hacisimsek.cart.dto.CheckoutResponse;
import com.hacisimsek.common.web.BusinessRuleException;
import com.hacisimsek.common.web.DependencyUnavailableException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * Places orders on order-service. Business rejections (4xx) surface as {@link BusinessRuleException};
 * timeouts, 5xx and an open circuit become {@link DependencyUnavailableException}. The idempotency
 * key makes a retried checkout return the original order instead of creating another one.
 */
public class OrderClient {

    static final String DEPENDENCY = "order-service";

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public OrderClient(RestClient restClient, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
    }

    public CheckoutResponse placeOrder(UUID customerId, List<OrderLine> lines, String idempotencyKey) {
        return circuitBreaker.run(() -> post(new PlaceOrder(customerId, lines), idempotencyKey), ex -> {
            if (ex instanceof BusinessRuleException rejection) {
                throw rejection;
            }
            throw new DependencyUnavailableException(DEPENDENCY, ex);
        });
    }

    private CheckoutResponse post(PlaceOrder body, String idempotencyKey) {
        try {
            return restClient.post()
                    .uri("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(body)
                    .retrieve()
                    .body(CheckoutResponse.class);
        } catch (HttpClientErrorException e) {
            ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
            throw new BusinessRuleException(problem != null && problem.getDetail() != null
                    ? problem.getDetail() : "Order rejected: " + e.getStatusCode());
        }
    }

    public record OrderLine(UUID productId, int quantity) {
    }

    record PlaceOrder(UUID customerId, List<OrderLine> items) {
    }
}
