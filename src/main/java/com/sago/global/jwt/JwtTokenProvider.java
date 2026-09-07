package com.sago.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Access·Refresh 토큰 발급과 검증을 담당한다.
 * subject에는 userId를 문자열로 담고, 토큰 종류는 "type" 클레임으로 구분한다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    /** HS256 서명에 필요한 최소 키 길이(256비트 = 32바이트). */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtTokenProvider(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET 환경변수가 없거나 너무 짧습니다. 최소 " + MIN_SECRET_BYTES + "바이트(영문 32자) 이상으로 설정하세요.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = properties.getAccessExpiration();
        this.refreshExpiration = properties.getRefreshExpiration();
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, TokenType.ACCESS, accessExpiration);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, TokenType.REFRESH, refreshExpiration);
    }

    public long getAccessExpirationSeconds() {
        return accessExpiration / 1000;
    }

    private String createToken(Long userId, TokenType type, long expirationMillis) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim(CLAIM_TYPE, type.name())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMillis))
            .signWith(key)
            .compact();
    }

    /**
     * 토큰을 검증하고 userId를 돌려준다.
     * 서명·만료뿐 아니라 토큰 종류까지 확인하므로, access 자리에 refresh를 넣으면 거부된다.
     */
    public Long parseUserId(String token, TokenType expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다.", e);
        }

        if (!expectedType.name().equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new InvalidTokenException(expectedType.name() + " 토큰이 아닙니다.");
        }

        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new InvalidTokenException("토큰의 사용자 식별자가 올바르지 않습니다.", e);
        }
    }
}
