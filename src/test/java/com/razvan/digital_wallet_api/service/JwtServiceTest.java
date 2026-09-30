package com.razvan.digital_wallet_api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private JwtService jwtService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();

        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET);
        ReflectionTestUtils.setField(jwtService, "expiration", 60_000L);
        ReflectionTestUtils.setField(
                jwtService, "refreshExpiration", 120_000L
        );

        signingKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET)
        );
    }

    @Test
    void refreshTokensShouldHaveUniqueIds() {
        Set<String> tokens = new HashSet<>();
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            String token =
                    jwtService.generateRefreshToken("user@test.com");

            Claims claims = parseClaims(token);

            assertNotNull(claims.getId());
            assertFalse(claims.getId().isBlank());
            assertTrue(tokens.add(token), "Duplicate refresh token");
            assertTrue(ids.add(claims.getId()), "Duplicate token id");

            assertEquals("user@test.com", claims.getSubject());
            assertEquals(
                    "REFRESH",
                    claims.get("tokenType", String.class)
            );
            assertTrue(
                    jwtService.isTokenValid(token, "user@test.com")
            );
        }
    }

    @Test
    void accessTokenShouldRemainValid() {
        String token =
                jwtService.generateAccessToken("user@test.com");

        assertNotNull(parseClaims(token).getId());
        assertEquals(
                "user@test.com",
                jwtService.extractEmail(token)
        );
        assertTrue(jwtService.isAccessToken(token));
        assertFalse(jwtService.isRefreshToken(token));
        assertTrue(
                jwtService.isTokenValid(token, "user@test.com")
        );
        assertFalse(
                jwtService.isTokenValid(token, "other@test.com")
        );
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}