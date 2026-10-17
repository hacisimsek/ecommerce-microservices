package com.hacisimsek.common.messaging;

import java.util.List;

/** Persistence for the transactional outbox. Implementations join the caller's transaction. */
public interface OutboxStore {

    void append(String topic, String key, String payloadType, String payload);

    /** Oldest messages first. Called inside the relay's transaction. */
    List<OutboxMessage> fetchBatch(int limit);

    void delete(String id);
}
