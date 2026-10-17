package com.hacisimsek.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional outbox publisher, its Kafka relay and the idempotent-consumer guard.
 * Active only when {@link MessagingStoresAutoConfiguration} registered a store.
 */
@AutoConfiguration(after = MessagingStoresAutoConfiguration.class,
        afterName = "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(OutboxStore.class)
public class MessagingAutoConfiguration {

    @Bean
    EventPublisher eventPublisher(OutboxStore store, ObjectMapper objectMapper) {
        return new EventPublisher(store, objectMapper);
    }

    @Bean
    IdempotencyGuard idempotencyGuard(ProcessedEventStore store,
                                      @Value("${spring.application.name}") String consumer) {
        return new IdempotencyGuard(store, consumer);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "outbox.relay.enabled", matchIfMissing = true)
    @EnableScheduling
    static class Relay {

        @Bean
        OutboxRelay outboxRelay(OutboxStore store, KafkaTemplate<String, Object> kafkaTemplate,
                                ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
            return new OutboxRelay(store, kafkaTemplate, objectMapper, new TransactionTemplate(transactionManager));
        }
    }
}
