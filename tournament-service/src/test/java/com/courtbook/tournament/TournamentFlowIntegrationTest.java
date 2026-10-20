package com.courtbook.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestTokens.Config.class)
class TournamentFlowIntegrationTest {

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

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };
    private static final String ADMIN = TestTokens.admin(1);

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    @Test
    void anAdminRunsAFivePlayerTournamentFromSignUpToChampion() {
        long id = createTournament("Fall Singles");

        for (long user = 101; user <= 105; user++) {
            assertThat(register(id, user).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        assertThat(register(id, 103).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map<String, Object>> memberStart = post("/v1/tournaments/" + id + "/start",
                TestTokens.member(101), null);
        assertThat(memberStart.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> started = post("/v1/tournaments/" + id + "/start", ADMIN, null).getBody();
        List<Map<String, Object>> rounds = list(started, "rounds");
        assertThat(rounds).hasSize(3);
        assertThat(rounds.getLast()).containsEntry("name", "Final");
        // 5 players in an 8 bracket: 3 byes in round one.
        long byes = list(rounds.getFirst(), "matches").stream().filter(m -> Boolean.TRUE.equals(m.get("bye"))).count();
        assertThat(byes).isEqualTo(3);

        assertThat(register(id, 106).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Play every ready match (player 1 always wins) until there's a champion.
        int played = 0;
        Map<String, Object> detail = started;
        while (!"COMPLETED".equals(summary(detail).get("status"))) {
            Map<String, Object> ready = list(detail, "rounds").stream()
                    .flatMap(r -> list(r, "matches").stream())
                    .filter(m -> "READY".equals(m.get("status")))
                    .findFirst().orElseThrow();
            long matchId = num(ready.get("id"));
            long winner = num(ready.get("player1Id"));
            ResponseEntity<Map<String, Object>> res = post("/v1/matches/" + matchId + "/result", ADMIN,
                    Map.of("winnerId", winner, "score", "21-15, 21-18"));
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            played++;
            detail = get("/v1/tournaments/" + id);
        }
        assertThat(played).isEqualTo(4); // 5 players, one eliminated per match
        assertThat(summary(detail).get("championId")).isNotNull();
        assertThat(summary(detail).get("championName")).isNotNull();

        List<String> events = jdbc.queryForList(
                "select type from outbox_events where payload like ? order by created_at", String.class,
                "%\"tournamentId\":" + id + ",%");
        assertThat(events).first().isEqualTo("TournamentStarted");
        assertThat(events).filteredOn("MatchResultRecorded"::equals).hasSize(4);
        assertThat(events).last().isEqualTo("TournamentCompleted");
    }

    @Test
    void resultsAreValidated() {
        long id = createTournament("Validation Cup");
        register(id, 201);
        register(id, 202);
        Map<String, Object> started = post("/v1/tournaments/" + id + "/start", ADMIN, null).getBody();
        Map<String, Object> fin = list(list(started, "rounds").getFirst(), "matches").getFirst();
        long matchId = num(fin.get("id"));

        assertThat(post("/v1/matches/" + matchId + "/result", ADMIN, Map.of("winnerId", 999)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/v1/matches/" + matchId + "/result", TestTokens.member(201), Map.of("winnerId", 201))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/v1/matches/" + matchId + "/result", ADMIN, Map.of("winnerId", 202)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/v1/matches/" + matchId + "/result", ADMIN, Map.of("winnerId", 201)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void ratingsUpdateAfterMatches() {
        long id = createTournament("Ratings Open");
        register(id, 301);
        register(id, 302);
        Map<String, Object> started = post("/v1/tournaments/" + id + "/start", ADMIN, null).getBody();
        long matchId = num(list(list(started, "rounds").getFirst(), "matches").getFirst().get("id"));
        post("/v1/matches/" + matchId + "/result", ADMIN, Map.of("winnerId", 302));

        List<Map<String, Object>> rankings = http.get().uri("/v1/rankings")
                .header("Authorization", "Bearer " + TestTokens.member(301))
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
        Map<String, Object> winner = rankings.stream().filter(r -> num(r.get("userId")) == 302).findFirst().orElseThrow();
        Map<String, Object> loser = rankings.stream().filter(r -> num(r.get("userId")) == 301).findFirst().orElseThrow();
        assertThat(num(winner.get("rating"))).isGreaterThan(num(loser.get("rating")));
        assertThat(num(winner.get("wins"))).isGreaterThanOrEqualTo(1);
    }

    @Test
    void signUpClosesAtTheDeadline() {
        long id = createTournament("Closing Soon");
        jdbc.update("update tournaments set signup_deadline = now() - interval '1 minute' where id = ?", id);
        assertThat(register(id, 401).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cannotStartWithFewerThanTwoPlayers() {
        long id = createTournament("Lonely Cup");
        register(id, 501);
        assertThat(post("/v1/tournaments/" + id + "/start", ADMIN, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private long createTournament(String name) {
        ResponseEntity<Map<String, Object>> res = post("/v1/tournaments", ADMIN, Map.of(
                "name", name,
                "startsOn", LocalDate.now().plusDays(7).toString(),
                "signupDeadline", Instant.now().plus(Duration.ofDays(3)).toString()));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return num(summary(res.getBody()).get("id"));
    }

    private ResponseEntity<Map<String, Object>> register(long tournamentId, long userId) {
        return post("/v1/tournaments/" + tournamentId + "/registrations", TestTokens.member(userId), null);
    }

    private ResponseEntity<Map<String, Object>> post(String path, String token, Object body) {
        var req = http.post().uri(path).header("Authorization", "Bearer " + token);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return req.retrieve().toEntity(JSON);
    }

    private Map<String, Object> get(String path) {
        return http.get().uri(path).header("Authorization", "Bearer " + ADMIN).retrieve().body(JSON);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(Map<String, Object> detail) {
        return (Map<String, Object>) detail.get("tournament");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> map, String key) {
        return (List<Map<String, Object>>) map.get(key);
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }
}
