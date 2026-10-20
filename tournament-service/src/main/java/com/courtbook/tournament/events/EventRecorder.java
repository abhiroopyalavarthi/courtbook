package com.courtbook.tournament.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes an event to the outbox table inside the caller's transaction. If the
 * tournament change rolls back, so does the event; if it commits, the relay is
 * guaranteed to see the event and send it.
 *
 * <p>Every event has the same envelope:
 * {"eventId", "type", "occurredAt", "source", "requestId", "payload": {...}}.
 * The formats are documented in docs/events.md.
 */
@Component
public class EventRecorder {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public EventRecorder(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(String type, Map<String, Object> payload) {
        UUID id = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", id.toString());
        envelope.put("type", type);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("source", "tournament-service");
        envelope.put("requestId", MDC.get("requestId"));
        envelope.put("payload", payload);
        jdbc.update("insert into outbox_events (id, type, payload) values (?, ?, ?)",
                id, type, json.writeValueAsString(envelope));
        return id;
    }
}
