package com.hacisimsek.cart.config;

import com.hacisimsek.cart.client.OrderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class OrderClientConfiguration {

    @Bean
    @LoadBalanced
    RestClient.Builder orderRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        // Creating an order includes a catalog lookup, so allow a little longer than a plain read
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    OrderClient orderClient(RestClient.Builder orderRestClientBuilder, CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                            @Value("${order.base-url:http://order-service}") String baseUrl) {
        return new OrderClient(orderRestClientBuilder.baseUrl(baseUrl).build(),
                circuitBreakerFactory.create("order-service"));
    }
}
