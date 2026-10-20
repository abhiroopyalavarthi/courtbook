package com.courtbook.notification.events;

import com.courtbook.notification.NotificationProperties;
import com.courtbook.notification.store.NotificationStore;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Handles one queue message. Returns normally when the message can be
 * deleted (handled, a duplicate, or not for us); throws when it should be
 * retried. After 5 failed tries SQS moves it to the dead-letter queue.
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    public enum Outcome { HANDLED, DUPLICATE, IGNORED }

    private final ObjectMapper json;
    private final NotificationStore store;
    private final EventTranslator translator;

    public EventConsumer(ObjectMapper json, NotificationStore store, NotificationProperties props) {
        this.json = json;
        this.store = store;
        this.translator = new EventTranslator(props.zone());
    }

    public Outcome handle(String body) {
        EventEnvelope event = json.readValue(body, EventEnvelope.class);
        if (event.eventId() == null || event.type() == null) {
            log.warn("Dropping message without eventId/type: {}", body);
            return Outcome.IGNORED;
        }
        if (event.requestId() != null) {
            MDC.put("requestId", event.requestId());
        }
        try {
            if (!translator.understands(event.type())) {
                log.info("Ignoring event type {} ({})", event.type(), event.eventId());
                return Outcome.IGNORED;
            }
            EventTranslator.Output out = translator.translate(event);
            Instant occurredAt = event.occurredAt() != null ? Instant.parse(event.occurredAt()) : Instant.now();
            boolean fresh = store.apply(event.eventId(), occurredAt, out.notifications(), out.feed());
            if (!fresh) {
                log.info("Skipping duplicate event {} ({})", event.eventId(), event.type());
                return Outcome.DUPLICATE;
            }
            log.info("Handled {} {}: {} notifications, {} feed items", event.type(), event.eventId(),
                    out.notifications().size(), out.feed().size());
            return Outcome.HANDLED;
        } finally {
            MDC.remove("requestId");
        }
    }
}
