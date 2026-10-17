package com.hacisimsek.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cart")
public record CartProperties(Duration ttl, int maxQuantityPerItem) {
}
