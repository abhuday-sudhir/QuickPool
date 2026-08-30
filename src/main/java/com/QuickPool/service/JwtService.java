package com.QuickPool.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    /** Must match the fallback in application.yml. */
    private static final String DEV_SECRET = "dev-only-insecure-secret-min-32-chars-change-me";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${spring.profiles.active:default}")
    private String activeProfiles;

    @Value("${app.jwt.access-token-minutes}")
    private long accessTokenMinutes;

    @Value("${app.jwt.refresh-token-days}")
    private long refreshTokenDays;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256. Set the JWT_SECRET environment variable.");
        }
        if (DEV_SECRET.equals(secret)) {
            if (activeProfiles.contains("prod")) {
                throw new IllegalStateException(
                        "Refusing to start with the development JWT secret under the prod profile. "
                                + "Set JWT_SECRET (openssl rand -base64 48).");
            }
            log.warn("Using the built-in DEVELOPMENT JWT secret. Set JWT_SECRET before deploying.");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId) {
        return generateToken(userId, accessTokenMinutes * 60 * 1000, "access");
    }

    /** Refresh tokens carry a jti so each one can be tracked and single-used. */
    public String generateRefreshToken(UUID userId, UUID jti) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenDays * 24L * 60 * 60 * 1000);
        return Jwts.builder()
                .subject(userId.toString())
                .id(jti.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    public UUID extractJti(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build()
                .parseSignedClaims(token).getPayload();
        String id = claims.getId();
        return id == null ? null : UUID.fromString(id);
    }

    public long refreshTokenDays() {
        return refreshTokenDays;
    }

    private String generateToken(UUID userId, long validityMillis, String type) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMillis);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    public UUID extractUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public boolean isValid(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractType(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("type", String.class);
    }
}