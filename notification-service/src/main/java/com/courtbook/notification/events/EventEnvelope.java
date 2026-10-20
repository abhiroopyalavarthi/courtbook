package com.courtbook.notification.events;

import java.util.List;
import java.util.Map;

/** The shape every service publishes. See docs/events.md. */
public record EventEnvelope(
        String eventId,
        String type,
        String occurredAt,
        String source,
        String requestId,
        Map<String, Object> payload) {

    public String str(String key) {
        Object v = payload == null ? null : payload.get(key);
        return v == null ? null : v.toString();
    }

    public long lng(String key) {
        Object v = payload == null ? null : payload.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            return Long.parseLong(s);
        }
        throw new IllegalArgumentException("Event " + eventId + " has no numeric '" + key + "'");
    }

    public List<Long> longs(String key) {
        Object v = payload == null ? null : payload.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(o -> ((Number) o).longValue()).toList();
    }
}
