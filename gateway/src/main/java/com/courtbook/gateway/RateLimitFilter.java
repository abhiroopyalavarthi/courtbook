package com.courtbook.gateway;

import java.net.InetSocketAddress;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Per-IP limits: a general one, and a much lower one for login attempts. */
@Component
@Order(-200) // before Spring Security (-100), so floods never reach token checks
public class RateLimitFilter implements WebFilter {

    private static final long MINUTE = 60_000;

    private final FixedWindowRateLimiter general;
    private final FixedWindowRateLimiter logins;
    private final boolean trustForwardedFor;

    public RateLimitFilter(GatewayProperties props) {
        this.general = new FixedWindowRateLimiter(props.rateLimit().requestsPerMinute(), MINUTE,
                System::currentTimeMillis);
        this.logins = new FixedWindowRateLimiter(props.rateLimit().loginsPerMinute(), MINUTE,
                System::currentTimeMillis);
        this.trustForwardedFor = props.rateLimit().trustForwardedFor();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!request.getPath().value().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        String ip = clientIp(request);
        boolean isLogin = HttpMethod.POST.equals(request.getMethod())
                && "/api/auth/login".equals(request.getPath().value());
        FixedWindowRateLimiter limiter = isLogin ? logins : general;
        if (limiter.tryAcquire(ip)) {
            return chain.filter(exchange);
        }
        long retryAfterSeconds = Math.max(1, limiter.millisUntilReset() / 1000);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return exchange.getResponse().setComplete();
    }

    private String clientIp(ServerHttpRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}
