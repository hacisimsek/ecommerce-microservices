package com.hacisimsek.cart.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.cart.config.CartProperties;
import com.hacisimsek.cart.model.Cart;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** One JSON document per customer under {@code cart:<customerId>}, expiring after {@code cart.ttl}. */
@Repository
public class CartRepository {

    private static final String KEY_PREFIX = "cart:";

    private final RedisTemplate<String, Cart> redis;
    private final CartProperties properties;

    public CartRepository(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                          CartProperties properties) {
        RedisTemplate<String, Cart> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, Cart.class));
        template.afterPropertiesSet();
        this.redis = template;
        this.properties = properties;
    }

    public Optional<Cart> find(UUID customerId) {
        return Optional.ofNullable(redis.opsForValue().get(key(customerId)));
    }

    public Cart save(Cart cart) {
        redis.opsForValue().set(key(cart.customerId()), cart, properties.ttl());
        return cart;
    }

    public void delete(UUID customerId) {
        redis.delete(key(customerId));
    }

    private static String key(UUID customerId) {
        return KEY_PREFIX + customerId;
    }
}
