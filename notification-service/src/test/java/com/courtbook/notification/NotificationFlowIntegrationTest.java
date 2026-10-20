package com.courtbook.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Runs the real consumer against ElasticMQ and DynamoDB Local, the same
 * stand-ins Docker Compose uses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"courtbook.dynamodb.create-tables=true", "courtbook.events.wait-seconds=1"})
@Import(TestTokens.Config.class)
class NotificationFlowIntegrationTest {

    static final GenericContainer<?> ELASTICMQ =
            new GenericContainer<>(DockerImageName.parse("softwaremill/elasticmq-native:latest")).withExposedPorts(9324);
    static final GenericContainer<?> DYNAMODB =
            new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest"))
                    .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
                    .withExposedPorts(8000);
    static final SqsClient SQS;

    static {
        // The SDK's default credential chain reads these; the local stand-ins accept anything.
        System.setProperty("aws.accessKeyId", "local");
        System.setProperty("aws.secretAccessKey", "local");
        ELASTICMQ.start();
        DYNAMODB.start();
        SQS = SqsClient.builder().region(Region.US_EAST_1).endpointOverride(URI.create(sqsEndpoint())).build();
        SQS.createQueue(r -> r.queueName("courtbook-events"));
    }

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("courtbook.aws.endpoint", NotificationFlowIntegrationTest::sqsEndpoint);
        registry.add("courtbook.dynamodb.endpoint",
                () -> "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000));
    }

    private static String sqsEndpoint() {
        return "http://" + ELASTICMQ.getHost() + ":" + ELASTICMQ.getMappedPort(9324);
    }

    @Value("${local.server.port}")
    int port;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    @Test
    void aWaitlistPromotionBecomesOneNotificationEvenIfDeliveredTwice() {
        long userId = 7001;
        String event = """
                {"eventId":"%s","type":"WaitlistPromoted","occurredAt":"%s","source":"booking-service",
                 "requestId":"req-1","payload":{"bookingId":9,"slotId":3,"userId":%d,"userName":"Abhi",
                 "courtName":"Court 3","startsAt":"2026-10-21T23:00:00Z","endsAt":"2026-10-22T00:00:00Z",
                 "source":"WAITLIST"}}""".formatted(UUID.randomUUID(), Instant.now(), userId);
        send(event);
        send(event); // SQS is at-least-once; simulate a redelivery

        Map<String, Object> page = waitFor(() -> notifications(userId), p -> ((Number) p.get("unreadCount")).intValue() >= 1);
        // Give the duplicate time to be (not) applied, then check again.
        sleep(3_000);
        page = notifications(userId);
        List<?> items = (List<?>) page.get("items");
        assertThat(items).hasSize(1);
        assertThat(page.get("unreadCount")).isEqualTo(1);
        assertThat(((Map<?, ?>) items.getFirst()).get("title")).isEqualTo("You got the court!");

        http.post().uri("/v1/notifications/read")
                .header("Authorization", "Bearer " + TestTokens.member(userId))
                .retrieve().toBodilessEntity();
        assertThat(notifications(userId).get("unreadCount")).isEqualTo(0);
    }

    @Test
    void matchResultsShowUpInTheClubFeed() {
        String event = """
                {"eventId":"%s","type":"MatchResultRecorded","occurredAt":"%s","source":"tournament-service",
                 "payload":{"tournamentId":1,"tournamentName":"Fall Singles","matchId":5,"round":3,
                 "roundName":"Final","winnerId":8001,"winnerName":"Priya","loserId":8002,"loserName":"Sam",
                 "score":"21-19, 21-18"}}""".formatted(UUID.randomUUID(), Instant.now());
        send(event);

        List<Map<String, Object>> feed = waitFor(this::feed,
                f -> f.stream().anyMatch(i -> String.valueOf(i.get("text")).startsWith("Priya beat Sam")));
        assertThat(feed).isNotEmpty();
        assertThat(((Number) notifications(8002).get("unreadCount")).intValue()).isEqualTo(1);
    }

    @Test
    void notificationsRequireAToken() {
        var res = http.get().uri("/v1/notifications").retrieve().toBodilessEntity();
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    private void send(String body) {
        String url = SQS.getQueueUrl(r -> r.queueName("courtbook-events")).queueUrl();
        SQS.sendMessage(r -> r.queueUrl(url).messageBody(body));
    }

    private Map<String, Object> notifications(long userId) {
        return http.get().uri("/v1/notifications")
                .header("Authorization", "Bearer " + TestTokens.member(userId))
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    private List<Map<String, Object>> feed() {
        return http.get().uri("/v1/feed")
                .header("Authorization", "Bearer " + TestTokens.member(1))
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    private static <T> T waitFor(Supplier<T> fetch, java.util.function.Predicate<T> done) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        T last = fetch.get();
        while (!done.test(last)) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting; last value: " + last);
            }
            sleep(250);
            last = fetch.get();
        }
        return last;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
