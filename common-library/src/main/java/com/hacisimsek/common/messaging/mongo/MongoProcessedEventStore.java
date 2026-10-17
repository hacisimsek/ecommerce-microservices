package com.hacisimsek.common.messaging.mongo;

import com.hacisimsek.common.messaging.ProcessedEventStore;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.UUID;

/**
 * The document id is "consumer:eventId", so the primary key itself rejects a second insert
 * if two deliveries race; the losing transaction aborts and is retried by the error handler.
 */
public class MongoProcessedEventStore implements ProcessedEventStore {

    private static final String COLLECTION = "processed_events";

    private final MongoTemplate mongo;

    public MongoProcessedEventStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean markProcessed(UUID eventId, String consumer) {
        String id = consumer + ":" + eventId;
        if (mongo.exists(Query.query(Criteria.where("_id").is(id)), COLLECTION)) {
            return false;
        }
        mongo.insert(new Document("_id", id).append("processedAt", Instant.now()), COLLECTION);
        return true;
    }
}
