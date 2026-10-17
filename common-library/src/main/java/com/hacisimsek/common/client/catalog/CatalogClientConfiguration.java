package com.hacisimsek.common.client.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Opt-in wiring for {@link CatalogClient}: {@code @Import(CatalogClientConfiguration.class)}.
 * The base URL is a service id resolved through Eureka by the load-balanced builder.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogClientConfiguration {

    @Bean
    @LoadBalanced
    RestClient.Builder catalogRestClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    CatalogClient catalogClient(RestClient.Builder catalogRestClientBuilder,
                                CircuitBreakerFactory<?, ?> circuitBreakerFactory,
                                @Value("${catalog.base-url:http://catalog-service}") String baseUrl) {
        return new CatalogClient(catalogRestClientBuilder.baseUrl(baseUrl).build(),
                circuitBreakerFactory.create(CatalogClient.DEPENDENCY));
    }
}
