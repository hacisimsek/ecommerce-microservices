package com.hacisimsek.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Kafka defaults shared by every service: saga topics, their dead letter topics and an error
 * handler that retries with backoff before parking a record on {@code <topic>.DLT}.
 * Serializer settings live in {@code shared-defaults.yml}.
 */
@Slf4j
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
public class CommonKafkaAutoConfiguration {

    private static final int PARTITIONS = 3;
    private static final int MAX_RETRIES = 4;

    @Bean
    public KafkaAdmin.NewTopics sagaTopics() {
        List<NewTopic> topics = new ArrayList<>();
        // Dead letter topics need the same partition count: a record keeps its partition
        Stream.of(KafkaTopics.ALL).forEach(name -> {
            topics.add(TopicBuilder.name(name).partitions(PARTITIONS).build());
            topics.add(TopicBuilder.name(name + KafkaTopics.DLT_SUFFIX).partitions(PARTITIONS).build());
        });
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    /**
     * Retries a failing record with exponential backoff (0.5s, 1s, 2s, 4s), then publishes it to
     * the dead letter topic with the exception in its headers. Records that can never succeed
     * (undeserializable payloads, conversion errors) go to the DLT immediately.
     */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> jsonTemplate,
                                                 KafkaProperties kafkaProperties) {
        // A record that failed deserialization only has its raw bytes, which the JSON template cannot write
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                kafkaProperties.buildProducerProperties(null), new StringSerializer(), new ByteArraySerializer())));
        templates.put(Object.class, jsonTemplate);

        // Same partition on "<topic>.DLT"; the original topic, offset, consumer group and
        // exception travel in the record's kafka_dlt-* headers
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> new TopicPartition(record.topic() + KafkaTopics.DLT_SUFFIX, record.partition()));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Sending record to DLT topic={} key={} offset={}: {}",
                    record.topic(), record.key(), record.offset(), ex.getMessage());
            recoverer.accept(record, ex);
        }, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
