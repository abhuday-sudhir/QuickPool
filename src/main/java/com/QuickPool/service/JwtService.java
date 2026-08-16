package com.QuickPool.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-minutes}")
    private long accessTokenMinutes;

    @Value("${app.jwt.refresh-token-days}")
    private long refreshTokenDays;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateAccessToken(UUID userId) {
        return generateToken(userId, accessTokenMinutes * 60 * 1000, "access");
    }

    public String generateRefreshToken(UUID userId) {
        return generateToken(userId, refreshTokenDays * 24 * 60 * 60 * 1000, "refresh");
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