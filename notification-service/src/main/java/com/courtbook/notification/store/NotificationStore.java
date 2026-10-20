package com.courtbook.notification.store;

import com.courtbook.notification.NotificationProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * DynamoDB access for notifications, the club feed and event de-duplication.
 *
 * <p>Tables (names get the configured prefix, e.g. "courtbook-"):
 * <ul>
 *   <li>notifications: partition key userId, sort key sk. A user's
 *       notifications have sk = "N#&lt;time&gt;#&lt;eventId&gt;", so one query
 *       returns them newest first. One extra item per user, sk = "READ",
 *       remembers when they last opened the bell; anything newer is unread.</li>
 *   <li>feed: partition key feedId (always "club"), sort key time + eventId.</li>
 *   <li>processed-events: partition key eventId. Writing an event's ID here
 *       with "only if it doesn't exist" is how duplicates are caught.</li>
 * </ul>
 */
@Component
public class NotificationStore implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(NotificationStore.class);
    private static final DateTimeFormatter SORTABLE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);
    private static final String FEED_ID = "club";
    private static final String READ_MARKER = "READ";
    /** DynamoDB allows at most 100 items in one transaction. */
    private static final int MAX_TRANSACTION_ITEMS = 100;
    private static final long PROCESSED_EVENT_TTL_DAYS = 14;

    private final DynamoDbClient dynamo;
    private final NotificationProperties props;
    private final String notificationsTable;
    private final String feedTable;
    private final String processedTable;

    public NotificationStore(DynamoDbClient dynamo, NotificationProperties props) {
        this.dynamo = dynamo;
        this.props = props;
        String prefix = props.dynamodb().tablePrefix();
        this.notificationsTable = prefix + "notifications";
        this.feedTable = prefix + "feed";
        this.processedTable = prefix + "processed-events";
    }

    public record NewNotification(long userId, String type, String title, String body) {
    }

    public record NewFeedItem(String type, String text) {
    }

    public record Notification(String id, String type, String title, String body, Instant createdAt, boolean read) {
    }

    public record FeedItem(String id, String type, String text, Instant createdAt) {
    }

    public record NotificationPage(int unreadCount, List<Notification> items) {
    }

    @Override
    public void afterPropertiesSet() {
        if (props.dynamodb().createTables()) {
            createTableIfMissing(notificationsTable, "userId", "sk");
            createTableIfMissing(feedTable, "feedId", "sk");
            createTableIfMissing(processedTable, "eventId", null);
        }
    }

    /**
     * Writes everything an event produces, plus its processed marker, in one
     * DynamoDB transaction. Returns false if the event was already processed,
     * in which case nothing is written.
     */
    public boolean apply(String eventId, Instant occurredAt, List<NewNotification> notifications,
            List<NewFeedItem> feedItems) {
        List<TransactWriteItem> writes = new ArrayList<>();
        String time = sortable(occurredAt);
        for (NewNotification n : notifications) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("userId", s(String.valueOf(n.userId())));
            item.put("sk", s("N#" + time + "#" + eventId));
            item.put("eventType", s(n.type()));
            item.put("title", s(n.title()));
            item.put("body", s(n.body()));
            item.put("createdAt", s(time));
            writes.add(put(notificationsTable, item));
        }
        for (int i = 0; i < feedItems.size(); i++) {
            NewFeedItem f = feedItems.get(i);
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("feedId", s(FEED_ID));
            item.put("sk", s(time + "#" + eventId + "#" + i));
            item.put("eventType", s(f.type()));
            item.put("text", s(f.text()));
            item.put("createdAt", s(time));
            writes.add(put(feedTable, item));
        }

        Map<String, AttributeValue> marker = new HashMap<>();
        marker.put("eventId", s(eventId));
        marker.put("processedAt", s(sortable(Instant.now())));
        marker.put("expiresAt", AttributeValue.builder()
                .n(String.valueOf(Instant.now().plus(PROCESSED_EVENT_TTL_DAYS, ChronoUnit.DAYS).getEpochSecond()))
                .build());
        TransactWriteItem markerWrite = TransactWriteItem.builder().put(Put.builder()
                .tableName(processedTable)
                .item(marker)
                .conditionExpression("attribute_not_exists(eventId)")
                .build()).build();

        // Normal case: everything plus the marker in one transaction. If an
        // event ever fans out past 99 items, the earlier chunks go first; their
        // keys are derived from the event, so re-sending them just overwrites.
        int start = 0;
        while (writes.size() - start > MAX_TRANSACTION_ITEMS - 1) {
            transact(writes.subList(start, start + MAX_TRANSACTION_ITEMS));
            start += MAX_TRANSACTION_ITEMS;
        }
        List<TransactWriteItem> last = new ArrayList<>(writes.subList(start, writes.size()));
        last.add(markerWrite);
        try {
            transact(last);
            return true;
        } catch (TransactionCanceledException e) {
            boolean duplicate = e.cancellationReasons().stream()
                    .map(CancellationReason::code)
                    .anyMatch("ConditionalCheckFailed"::equals);
            if (duplicate) {
                return false;
            }
            throw e;
        }
    }

    public NotificationPage notificationsFor(long userId, int limit) {
        String user = String.valueOf(userId);
        String lastRead = lastReadAt(user);
        QueryResponse page = dynamo.query(QueryRequest.builder()
                .tableName(notificationsTable)
                .keyConditionExpression("userId = :u and begins_with(sk, :prefix)")
                .expressionAttributeValues(Map.of(":u", s(user), ":prefix", s("N#")))
                .scanIndexForward(false)
                .limit(limit)
                .build());
        List<Notification> items = page.items().stream().map(item -> {
            String createdAt = item.get("createdAt").s();
            boolean read = lastRead != null && createdAt.compareTo(lastRead) <= 0;
            return new Notification(item.get("sk").s(), item.get("eventType").s(), item.get("title").s(),
                    item.get("body").s(), parse(createdAt), read);
        }).toList();

        // Everything sorted after "N#<lastRead>~" is newer than the last read time.
        String from = lastRead == null ? "N#" : "N#" + lastRead + "~";
        QueryResponse unread = dynamo.query(QueryRequest.builder()
                .tableName(notificationsTable)
                .keyConditionExpression("userId = :u and sk between :from and :to")
                .expressionAttributeValues(Map.of(":u", s(user), ":from", s(from), ":to", s("N#~")))
                .select(Select.COUNT)
                .build());
        return new NotificationPage(unread.count(), items);
    }

    public void markAllRead(long userId, Instant now) {
        dynamo.putItem(r -> r.tableName(notificationsTable).item(Map.of(
                "userId", s(String.valueOf(userId)),
                "sk", s(READ_MARKER),
                "readAt", s(sortable(now)))));
    }

    public List<FeedItem> feed(int limit) {
        QueryResponse page = dynamo.query(QueryRequest.builder()
                .tableName(feedTable)
                .keyConditionExpression("feedId = :f")
                .expressionAttributeValues(Map.of(":f", s(FEED_ID)))
                .scanIndexForward(false)
                .limit(limit)
                .build());
        return page.items().stream()
                .map(item -> new FeedItem(item.get("sk").s(), item.get("eventType").s(), item.get("text").s(),
                        parse(item.get("createdAt").s())))
                .toList();
    }

    private String lastReadAt(String user) {
        Map<String, AttributeValue> item = dynamo.getItem(r -> r.tableName(notificationsTable)
                .key(Map.of("userId", s(user), "sk", s(READ_MARKER)))
                .consistentRead(true)).item();
        return item == null || item.isEmpty() ? null : item.get("readAt").s();
    }

    private void transact(List<TransactWriteItem> items) {
        dynamo.transactWriteItems(r -> r.transactItems(items));
    }

    private void createTableIfMissing(String table, String partitionKey, String sortKey) {
        try {
            dynamo.describeTable(r -> r.tableName(table));
            return;
        } catch (ResourceNotFoundException e) {
            // create below
        }
        List<AttributeDefinition> attributes = new ArrayList<>();
        List<KeySchemaElement> keys = new ArrayList<>();
        attributes.add(AttributeDefinition.builder().attributeName(partitionKey).attributeType(ScalarAttributeType.S).build());
        keys.add(KeySchemaElement.builder().attributeName(partitionKey).keyType(KeyType.HASH).build());
        if (sortKey != null) {
            attributes.add(AttributeDefinition.builder().attributeName(sortKey).attributeType(ScalarAttributeType.S).build());
            keys.add(KeySchemaElement.builder().attributeName(sortKey).keyType(KeyType.RANGE).build());
        }
        dynamo.createTable(r -> r.tableName(table)
                .attributeDefinitions(attributes)
                .keySchema(keys)
                .billingMode(BillingMode.PAY_PER_REQUEST));
        dynamo.waiter().waitUntilTableExists(r -> r.tableName(table));
        log.info("Created DynamoDB table {}", table);
    }

    private static TransactWriteItem put(String table, Map<String, AttributeValue> item) {
        return TransactWriteItem.builder().put(Put.builder().tableName(table).item(item).build()).build();
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value == null ? "" : value).build();
    }

    static String sortable(Instant instant) {
        return SORTABLE_TIME.format(instant.truncatedTo(ChronoUnit.MICROS));
    }

    private static Instant parse(String sortable) {
        return Instant.from(SORTABLE_TIME.parse(sortable));
    }
}
