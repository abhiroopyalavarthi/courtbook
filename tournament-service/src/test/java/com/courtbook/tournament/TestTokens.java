package com.courtbook.tournament;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Stands in for the user service in tests: mints tokens with a test key and
 * makes this service trust that key instead of fetching the real JWKS.
 */
public final class TestTokens {

    private static final RSAKey KEY;
    private static final JwtEncoder ENCODER;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
    }

    private TestTokens() {
    }

    public static String member(long userId) {
        return token(userId, "Player " + userId, "MEMBER");
    }

    public static String admin(long userId) {
        return token(userId, "Admin " + userId, "ADMIN");
    }

    public static String token(long userId, String name, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("courtbook")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .claim("email", "user" + userId + "@courtbook.test")
                .claim("name", name)
                .claim("roles", List.of(role))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder() throws JOSEException {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        }
    }
}
