package com.courtbook.user.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("courtbook.jwt")
public record JwtProperties(String issuer, Duration ttl, String privateKey) {
}
