package com.hacisimsek.common.messaging.jdbc;

import com.hacisimsek.common.messaging.ProcessedEventStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public class JdbcProcessedEventStore implements ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public JdbcProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(UUID eventId, String consumer) {
        return jdbc.update("INSERT INTO processed_events (event_id, consumer) VALUES (?, ?) ON CONFLICT DO NOTHING",
                eventId, consumer) == 1;
    }
}
