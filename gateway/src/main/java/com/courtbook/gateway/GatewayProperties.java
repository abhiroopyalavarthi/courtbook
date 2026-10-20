package com.courtbook.gateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("courtbook")
public record GatewayProperties(String jwksUri, Routes routes, Cors cors, RateLimit rateLimit) {

    public record Routes(String user, String booking, String tournament, String notification) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record RateLimit(int requestsPerMinute, int loginsPerMinute, boolean trustForwardedFor) {
    }
}
