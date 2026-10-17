package com.hacisimsek.common.messaging.jdbc;

import com.hacisimsek.common.messaging.OutboxMessage;
import com.hacisimsek.common.messaging.OutboxStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** Outbox in the service's own Postgres database (table created by the service's Flyway migration). */
public class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbc;

    public JdbcOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String topic, String key, String payloadType, String payload) {
        jdbc.update("INSERT INTO outbox (topic, message_key, payload_type, payload) VALUES (?, ?, ?, ?::jsonb)",
                topic, key, payloadType, payload);
    }

    @Override
    public List<OutboxMessage> fetchBatch(int limit) {
        // SKIP LOCKED lets several instances of a service relay concurrently without double sends
        return jdbc.query("""
                        SELECT id, topic, message_key, payload_type, payload::text AS payload
                        FROM outbox ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED""",
                (rs, i) -> new OutboxMessage(rs.getString("id"), rs.getString("topic"), rs.getString("message_key"),
                        rs.getString("payload_type"), rs.getString("payload")),
                limit);
    }

    @Override
    public void delete(String id) {
        jdbc.update("DELETE FROM outbox WHERE id = ?", Long.parseLong(id));
    }
}
