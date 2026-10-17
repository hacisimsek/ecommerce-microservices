package com.hacisimsek.common.messaging;

import com.hacisimsek.common.messaging.jdbc.JdbcOutboxStore;
import com.hacisimsek.common.messaging.jdbc.JdbcProcessedEventStore;
import com.hacisimsek.common.messaging.mongo.MongoOutboxStore;
import com.hacisimsek.common.messaging.mongo.MongoProcessedEventStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Outbox and processed-event stores matching the service's database: Postgres services get the
 * JDBC stores, MongoDB services the Mongo ones. Runs before {@link MessagingAutoConfiguration},
 * whose beans depend on whichever store was registered here.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration"})
@ConditionalOnClass(KafkaTemplate.class)
public class MessagingStoresAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    static class JdbcStores {

        @Bean
        @ConditionalOnMissingBean
        OutboxStore jdbcOutboxStore(JdbcTemplate jdbc) {
            return new JdbcOutboxStore(jdbc);
        }

        @Bean
        @ConditionalOnMissingBean
        ProcessedEventStore jdbcProcessedEventStore(JdbcTemplate jdbc) {
            return new JdbcProcessedEventStore(jdbc);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoTemplate.class)
    @ConditionalOnBean(MongoTemplate.class)
    static class MongoStores {

        /** Mongo-only services get real transactions (requires a replica set). */
        @Bean
        @ConditionalOnMissingBean(PlatformTransactionManager.class)
        MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory factory) {
            return new MongoTransactionManager(factory);
        }

        @Bean
        @ConditionalOnMissingBean
        OutboxStore mongoOutboxStore(MongoTemplate mongo) {
            return new MongoOutboxStore(mongo);
        }

        @Bean
        @ConditionalOnMissingBean
        ProcessedEventStore mongoProcessedEventStore(MongoTemplate mongo) {
            return new MongoProcessedEventStore(mongo);
        }
    }
}
