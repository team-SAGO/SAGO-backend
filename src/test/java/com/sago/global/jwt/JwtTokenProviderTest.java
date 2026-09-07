package com.sago.global.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-only-secret-key-for-sago-backend-1234567890";

    private JwtTokenProvider provider(long accessExpiration, long refreshExpiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessExpiration(accessExpiration);
        properties.setRefreshExpiration(refreshExpiration);
        return new JwtTokenProvider(properties);
    }

    private JwtTokenProvider provider() {
        return provider(3_600_000L, 1_209_600_000L);
    }

    @Test
    @DisplayName("access 토큰을 발급하고 다시 파싱하면 원래 userId가 나온다")
    void createAndParseAccessToken() {
        JwtTokenProvider provider = provider();

        String token = provider.createAccessToken(42L);

        assertThat(provider.parseUserId(token, TokenType.ACCESS)).isEqualTo(42L);
    }

    @Test
    @DisplayName("refresh 토큰을 access 토큰 자리에 쓰면 거부된다")
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtTokenProvider provider = provider();

        String refreshToken = provider.createRefreshToken(42L);

        assertThatThrownBy(() -> provider.parseUserId(refreshToken, TokenType.ACCESS))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("다른 시크릿으로 서명된 토큰은 거부된다")
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("completely-different-secret-key-value-0987654321");
        otherProperties.setAccessExpiration(3_600_000L);
        otherProperties.setRefreshExpiration(1_209_600_000L);

        String foreignToken = new JwtTokenProvider(otherProperties).createAccessToken(42L);

        assertThatThrownBy(() -> provider().parseUserId(foreignToken, TokenType.ACCESS))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("만료된 토큰은 거부된다")
    void expiredTokenIsRejected() {
        // 만료 시간을 음수로 주면 발급 시점에 이미 만료된 토큰이 만들어진다.
        JwtTokenProvider expiredProvider = provider(-1_000L, -1_000L);

        String token = expiredProvider.createAccessToken(42L);

        assertThatThrownBy(() -> expiredProvider.parseUserId(token, TokenType.ACCESS))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("형식이 깨진 문자열은 거부된다")
    void malformedTokenIsRejected() {
        JwtTokenProvider provider = provider();

        assertThatThrownBy(() -> provider.parseUserId("not-a-jwt", TokenType.ACCESS))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("시크릿이 256비트 미만이면 기동 시점에 막는다")
    void tooShortSecretIsRejectedOnStartup() {
        JwtProperties shortSecret = new JwtProperties();
        shortSecret.setSecret("short");

        assertThatThrownBy(() -> new JwtTokenProvider(shortSecret))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }
}
