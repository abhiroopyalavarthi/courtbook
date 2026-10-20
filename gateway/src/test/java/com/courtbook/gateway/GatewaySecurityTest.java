package com.courtbook.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Checks the gateway's own rules. No backend services run here, so these
 * tests only cover what the gateway decides before forwarding.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "courtbook.cors.allowed-origins=http://localhost:5173")
class GatewaySecurityTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void healthIsPublic() throws Exception {
        HttpResponse<String> res = get("/actuator/health");
        assertThat(res.statusCode()).isEqualTo(200);
    }

    @Test
    void apiCallsWithoutTokenAreRejected() throws Exception {
        assertThat(get("/api/bookings/me").statusCode()).isEqualTo(401);
        assertThat(get("/api/notifications").statusCode()).isEqualTo(401);
        assertThat(get("/api/admin/stats/users").statusCode()).isEqualTo(401);
    }

    @Test
    void garbageTokenIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(uri("/api/bookings/me"))
                .header("Authorization", "Bearer not.a.token")
                .build();
        assertThat(client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        HttpResponse<String> res = get("/api/bookings/me");
        assertThat(res.headers().firstValue("X-Request-Id")).isPresent();
    }

    @Test
    void corsPreflightAllowsTheFrontendOnly() throws Exception {
        HttpRequest allowed = preflight("http://localhost:5173");
        HttpResponse<String> ok = client.send(allowed, HttpResponse.BodyHandlers.ofString());
        assertThat(ok.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");

        HttpResponse<String> denied = client.send(preflight("https://evil.example"), HttpResponse.BodyHandlers.ofString());
        assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private HttpRequest preflight(String origin) {
        return HttpRequest.newBuilder(uri("/api/bookings"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .build();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
