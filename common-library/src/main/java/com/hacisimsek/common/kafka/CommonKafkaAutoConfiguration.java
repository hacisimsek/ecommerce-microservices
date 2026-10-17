package com.hacisimsek.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka defaults shared by every service: saga topics and a bounded-retry error handler.
 * Serializer settings live in {@code common-kafka.yml}, imported by each service.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
public class CommonKafkaAutoConfiguration {

    private static final int PARTITIONS = 3;

    @Bean
    public KafkaAdmin.NewTopics sagaTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(KafkaTopics.ORDER_EVENTS).partitions(PARTITIONS).build(),
                TopicBuilder.name(KafkaTopics.INVENTORY_EVENTS).partitions(PARTITIONS).build(),
                TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS).partitions(PARTITIONS).build(),
                TopicBuilder.name(KafkaTopics.SHIPPING_EVENTS).partitions(PARTITIONS).build());
    }

    /**
     * Retries a failing record 3 times, then logs and skips it so one bad message
     * cannot block a partition. A dead letter topic replaces the skip in a later phase.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(
                (record, ex) -> log.error("Giving up on record topic={} key={} offset={}",
                        record.topic(), record.key(), record.offset(), ex),
                new FixedBackOff(1000L, 3));
    }
}
