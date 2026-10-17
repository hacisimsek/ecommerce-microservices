package com.hacisimsek.common.messaging.mongo;

import com.hacisimsek.common.messaging.OutboxMessage;
import com.hacisimsek.common.messaging.OutboxStore;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

/**
 * Outbox collection in the service's MongoDB. Writes are atomic with the business change only
 * inside a MongoDB transaction, which needs a replica set (see docker-compose.yml).
 */
public class MongoOutboxStore implements OutboxStore {

    static final String COLLECTION = "outbox";

    private final MongoTemplate mongo;

    public MongoOutboxStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void append(String topic, String key, String payloadType, String payload) {
        // ObjectIds increase within a process, so sorting by _id preserves write order
        mongo.insert(new Document("_id", new ObjectId())
                .append("topic", topic)
                .append("key", key)
                .append("payloadType", payloadType)
                .append("payload", payload)
                .append("createdAt", Instant.now()), COLLECTION);
    }

    @Override
    public List<OutboxMessage> fetchBatch(int limit) {
        Query query = new Query().with(Sort.by("_id")).limit(limit);
        return mongo.find(query, Document.class, COLLECTION).stream()
                .map(d -> new OutboxMessage(d.getObjectId("_id").toHexString(), d.getString("topic"),
                        d.getString("key"), d.getString("payloadType"), d.getString("payload")))
                .toList();
    }

    @Override
    public void delete(String id) {
        mongo.remove(Query.query(Criteria.where("_id").is(new ObjectId(id))), COLLECTION);
    }
}
