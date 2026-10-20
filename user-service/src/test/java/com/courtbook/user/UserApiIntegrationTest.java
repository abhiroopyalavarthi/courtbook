package com.courtbook.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtbook.user.account.UserRepository;
import com.courtbook.user.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "courtbook.admin-emails=boss@courtbook.test")
class UserApiIntegrationTest {

    // One container for the whole test JVM; Testcontainers' Ryuk removes it afterwards.
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

    @Value("${local.server.port}")
    int port;

    @Autowired
    TokenService tokenService;

    @Autowired
    UserRepository users;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { })
                .build();
    }

    @Test
    void registerLoginAndCallProtectedEndpoint() {
        String email = uniqueEmail();
        assertThat(register(email, "Abhi", "correct-horse").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String token = login(email, "correct-horse");

        ResponseEntity<Map<String, Object>> me = http.get().uri("/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .retrieve().toEntity(JSON);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("email", email).containsEntry("role", "MEMBER");
    }

    @Test
    void emailIsCaseInsensitiveAndUnique() {
        String email = uniqueEmail();
        register(email, "First", "password-one");
        ResponseEntity<Map<String, Object>> second = register(email.toUpperCase(), "Second", "password-two");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("title", "Conflict");
    }

    @Test
    void wrongPasswordIsRejected() {
        String email = uniqueEmail();
        register(email, "Abhi", "correct-horse");
        ResponseEntity<Map<String, Object>> res = postLogin(email, "wrong-horse");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("detail", "Invalid email or password");
    }

    @Test
    void unknownEmailGetsTheSameAnswerAsWrongPassword() {
        ResponseEntity<Map<String, Object>> res = postLogin(uniqueEmail(), "whatever-123");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody()).containsEntry("detail", "Invalid email or password");
    }

    @Test
    void invalidRegistrationReturnsValidationProblem() {
        ResponseEntity<Map<String, Object>> res = register("not-an-email", "", "short");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsKey("title");
    }

    @Test
    void missingTokenIsRejected() {
        ResponseEntity<String> res = http.get().uri("/v1/users/me").retrieve().toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsRejected() {
        String email = uniqueEmail();
        register(email, "Abhi", "correct-horse");
        var user = users.findByEmail(email).orElseThrow();
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        String expired = tokenService.issue(user, twoHoursAgo, twoHoursAgo.plus(Duration.ofHours(1))).value();

        ResponseEntity<String> res = http.get().uri("/v1/users/me")
                .header("Authorization", "Bearer " + expired)
                .retrieve().toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminEmailsBecomeAdminsAndMembersCannotUseAdminEndpoints() {
        String memberEmail = uniqueEmail();
        register(memberEmail, "Member", "password-123");
        String memberToken = login(memberEmail, "password-123");
        ResponseEntity<String> forbidden = http.get().uri("/v1/admin/users")
                .header("Authorization", "Bearer " + memberToken)
                .retrieve().toEntity(String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        users.findByEmail("boss@courtbook.test").ifPresent(users::delete);
        ResponseEntity<Map<String, Object>> admin = register("boss@courtbook.test", "Boss", "password-123");
        assertThat(admin.getBody()).containsEntry("role", "ADMIN");
        String adminToken = login("boss@courtbook.test", "password-123");
        ResponseEntity<String> ok = http.get().uri("/v1/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .retrieve().toEntity(String.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void jwksIsPublic() {
        ResponseEntity<Map<String, Object>> res = http.get().uri("/.well-known/jwks.json").retrieve().toEntity(JSON);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsKey("keys");
    }

    private ResponseEntity<Map<String, Object>> register(String email, String name, String password) {
        return http.post().uri("/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "name", name, "password", password))
                .retrieve().toEntity(JSON);
    }

    private ResponseEntity<Map<String, Object>> postLogin(String email, String password) {
        return http.post().uri("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().toEntity(JSON);
    }

    private String login(String email, String password) {
        ResponseEntity<Map<String, Object>> res = postLogin(email, password);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) res.getBody().get("accessToken");
    }

    private static String uniqueEmail() {
        return "player-" + UUID.randomUUID() + "@courtbook.test";
    }
}
