package com.courtbook.tournament.events;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Sends committed outbox events to SQS, oldest first.
 *
 * <p>If the process dies after sending but before marking a row as published,
 * that event is sent again on restart. That's fine: SQS is at-least-once
 * anyway, and the notification service skips event IDs it has already seen.
 */
@Component
@ConditionalOnProperty(name = "courtbook.events.enabled", havingValue = "true")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 50;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SqsClient sqs;
    private final EventsProperties props;
    private volatile String queueUrl;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate tx, SqsClient sqs, EventsProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.sqs = sqs;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${courtbook.events.relay-interval-ms:500}")
    public void relay() {
        try {
            tx.executeWithoutResult(status -> publishBatch());
        } catch (RuntimeException e) {
            log.warn("Outbox relay failed, will retry: {}", e.toString());
        }
    }

    private void publishBatch() {
        // SKIP LOCKED lets a second instance (if there ever is one) take other rows.
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, type, payload from outbox_events
                where published_at is null
                order by created_at
                limit ?
                for update skip locked""", BATCH);
        if (rows.isEmpty()) {
            return;
        }
        List<UUID> sent = new ArrayList<>();
        try {
            for (Map<String, Object> row : rows) {
                String type = (String) row.get("type");
                sqs.sendMessage(SendMessageRequest.builder()
                        .queueUrl(queueUrl())
                        .messageBody((String) row.get("payload"))
                        .messageAttributes(Map.of("type", MessageAttributeValue.builder()
                                .dataType("String").stringValue(type).build()))
                        .build());
                sent.add((UUID) row.get("id"));
            }
        } finally {
            // Mark whatever made it out, even if a later send failed.
            if (!sent.isEmpty()) {
                jdbc.update(con -> {
                    var ps = con.prepareStatement(
                            "update outbox_events set published_at = now() where id = any(?)");
                    Array ids = con.createArrayOf("uuid", sent.toArray());
                    ps.setArray(1, ids);
                    return ps;
                });
                log.debug("Published {} events", sent.size());
            }
        }
    }

    private String queueUrl() {
        if (queueUrl == null) {
            queueUrl = sqs.getQueueUrl(r -> r.queueName(props.queueName())).queueUrl();
        }
        return queueUrl;
    }
}
