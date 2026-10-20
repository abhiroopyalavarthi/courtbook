package com.courtbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "courtbook.schedule.max-active-bookings-per-user=3",
            // Enough connections that 100 racing requests really hit the database together.
            "spring.datasource.hikari.maximum-pool-size=30"
        })
@Import(TestTokens.Config.class)
abstract class IntegrationTestBase {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };
    static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST = new ParameterizedTypeReference<>() {
    };

    private static final AtomicLong USER_IDS = new AtomicLong(1_000);
    private static final AtomicInteger SLOT_CURSOR = new AtomicInteger();
    private static final int SLOTS_PER_DAY = 14 * 4;

    @Value("${local.server.port}")
    int port;

    RestClient http;

    @BeforeEach
    void setUpClient() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    /** A user id no other test has used, so per-user limits never collide. */
    static long newUser() {
        return USER_IDS.incrementAndGet();
    }

    /** A future slot that no other test has touched. */
    long freshSlot() {
        int n = SLOT_CURSOR.getAndIncrement();
        LocalDate date = LocalDate.now(ZoneId.of("America/New_York")).plusDays(1 + n / SLOTS_PER_DAY);
        List<Map<String, Object>> slots = slotsOn(date, newUser());
        assertThat(slots).hasSize(SLOTS_PER_DAY);
        return ((Number) slots.get(n % SLOTS_PER_DAY).get("id")).longValue();
    }

    List<Map<String, Object>> slotsOn(LocalDate date, long userId) {
        return http.get().uri("/v1/slots?date={d}", date)
                .header("Authorization", "Bearer " + TestTokens.member(userId))
                .retrieve().body(JSON_LIST);
    }

    Map<String, Object> slot(long slotId, LocalDate date, long viewer) {
        return slotsOn(date, viewer).stream()
                .filter(s -> ((Number) s.get("id")).longValue() == slotId)
                .findFirst().orElseThrow();
    }

    ResponseEntity<Map<String, Object>> book(long userId, long slotId) {
        return http.post().uri("/v1/bookings")
                .header("Authorization", "Bearer " + TestTokens.member(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("slotId", slotId))
                .retrieve().toEntity(JSON);
    }

    ResponseEntity<Void> cancel(String token, long bookingId) {
        return http.delete().uri("/v1/bookings/{id}", bookingId)
                .header("Authorization", "Bearer " + token)
                .retrieve().toBodilessEntity();
    }

    ResponseEntity<Map<String, Object>> joinWaitlist(long userId, long slotId) {
        return http.post().uri("/v1/slots/{id}/waitlist", slotId)
                .header("Authorization", "Bearer " + TestTokens.member(userId))
                .retrieve().toEntity(JSON);
    }

    List<Map<String, Object>> myBookings(long userId) {
        return http.get().uri("/v1/bookings/me")
                .header("Authorization", "Bearer " + TestTokens.member(userId))
                .retrieve().body(JSON_LIST);
    }

    static long id(ResponseEntity<Map<String, Object>> res) {
        return ((Number) res.getBody().get("id")).longValue();
    }
}
