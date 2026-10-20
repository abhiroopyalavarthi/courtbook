package com.courtbook.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The public API lives under /api/**. Each route strips that prefix and
 * forwards to the owning service's /v1/** endpoints.
 */
@Configuration
public class RoutesConfig {

    private static final String API_PREFIX = "/api/(?<rest>.*)";
    private static final String SERVICE_PATH = "/v1/${rest}";

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties props) {
        GatewayProperties.Routes to = props.routes();
        return builder.routes()
                .route("users", r -> r
                        .path("/api/users/**", "/api/auth/**", "/api/admin/users/**", "/api/admin/stats/users")
                        .filters(f -> f.rewritePath(API_PREFIX, SERVICE_PATH))
                        .uri(to.user()))
                .route("bookings", r -> r
                        .path("/api/courts/**", "/api/slots/**", "/api/bookings/**", "/api/admin/stats/bookings")
                        .filters(f -> f.rewritePath(API_PREFIX, SERVICE_PATH))
                        .uri(to.booking()))
                .route("tournaments", r -> r
                        .path("/api/tournaments/**", "/api/matches/**", "/api/rankings/**",
                                "/api/admin/stats/tournaments")
                        .filters(f -> f.rewritePath(API_PREFIX, SERVICE_PATH))
                        .uri(to.tournament()))
                .route("notifications", r -> r
                        .path("/api/notifications/**", "/api/feed/**")
                        .filters(f -> f.rewritePath(API_PREFIX, SERVICE_PATH))
                        .uri(to.notification()))
                .build();
    }
}
