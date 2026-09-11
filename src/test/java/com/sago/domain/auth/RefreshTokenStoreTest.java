package com.sago.domain.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장·조회·무효화를 실제 DB로 확인한다.
 * 해시를 저장하고 그 해시로 다시 찾는 왕복이라 목으로는 의미 있는 검증이 되지 않는다.
 */
@SpringBootTest
@Transactional
class RefreshTokenStoreTest {

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(
            User.builder().email("rider@example.com").nickname("라이더").build());
    }

    /** consume은 무효화까지 하므로, 존재 확인에는 리포지토리를 직접 본다. */
    private boolean stored(String token) {
        return refreshTokenRepository.findAll().stream()
            .anyMatch(row -> row.getTokenHash().equals(sha256Hex(token)));
    }

    private String sha256Hex(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of()
                .formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("저장한 토큰은 저장소에서 확인된다")
    void savedTokenIsFound() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());

        refreshTokenStore.save(user, token);

        assertThat(stored(token)).isTrue();
    }

    @Test
    @DisplayName("저장한 적 없는 토큰은 확인되지 않는다")
    void unknownTokenIsNotFound() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());

        assertThat(stored(token)).isFalse();
    }

    @Test
    @DisplayName("토큰 원문은 저장되지 않는다")
    void tokenValueIsNotStored() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());

        refreshTokenStore.save(user, token);

        assertThat(refreshTokenRepository.findAll())
            .allSatisfy(stored -> {
                assertThat(stored.getTokenHash()).isNotEqualTo(token);
                assertThat(stored.getTokenHash()).hasSize(64);
            });
    }

    @Test
    @DisplayName("무효화하면 true를 돌려주고 토큰이 사라진다")
    void consumeRemovesToken() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());
        refreshTokenStore.save(user, token);

        assertThat(refreshTokenStore.consume(token)).isTrue();
        assertThat(stored(token)).isFalse();
    }

    @Test
    @DisplayName("같은 토큰을 두 번 무효화하면 두 번째는 false다")
    void secondConsumeReturnsFalse() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());
        refreshTokenStore.save(user, token);

        // 경합에서 진 쪽이 받는 값이다. 이 false 하나로 재발급을 막는다.
        assertThat(refreshTokenStore.consume(token)).isTrue();
        assertThat(refreshTokenStore.consume(token)).isFalse();
    }

    @Test
    @DisplayName("없는 토큰을 무효화하면 오류 없이 false를 돌려준다")
    void consumingUnknownTokenReturnsFalse() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());

        assertThat(refreshTokenStore.consume(token)).isFalse();
    }

    @Test
    @DisplayName("회원의 토큰을 전부 무효화하면 다른 기기 토큰도 함께 사라진다")
    void revokeAllRemovesEveryDeviceToken() {
        String phone = jwtTokenProvider.createRefreshToken(user.getUserId());
        String tablet = jwtTokenProvider.createRefreshToken(user.getUserId()) + "-other-device";
        refreshTokenStore.save(user, phone);
        refreshTokenStore.save(user, tablet);

        refreshTokenStore.revokeAll(user.getUserId());

        assertThat(stored(phone)).isFalse();
        assertThat(stored(tablet)).isFalse();
    }

    @Test
    @DisplayName("다른 회원의 토큰은 무효화 대상에서 제외된다")
    void revokeAllDoesNotTouchOtherUsers() {
        User other = userRepository.save(User.builder().email("other@example.com").build());
        String mine = jwtTokenProvider.createRefreshToken(user.getUserId());
        String theirs = jwtTokenProvider.createRefreshToken(other.getUserId()) + "-other-user";
        refreshTokenStore.save(user, mine);
        refreshTokenStore.save(other, theirs);

        refreshTokenStore.revokeAll(user.getUserId());

        assertThat(stored(mine)).isFalse();
        assertThat(stored(theirs)).isTrue();
    }

    @Test
    @DisplayName("만료 정리는 만료된 토큰만 지우고 아직 쓸 수 있는 토큰은 남긴다")
    void deleteExpiredRemovesOnlyExpiredTokens() {
        String valid = jwtTokenProvider.createRefreshToken(user.getUserId());
        refreshTokenStore.save(user, valid);
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(sha256Hex("expired-device"))
            .expiresAt(LocalDateTime.now().minusMinutes(1))
            .build());

        assertThat(refreshTokenStore.deleteExpired()).isEqualTo(1);
        assertThat(stored("expired-device")).isFalse();
        assertThat(stored(valid)).isTrue();
    }

    @Test
    @DisplayName("만료 정리를 연달아 돌리면 두 번째는 0건이다 — 서버 여러 대가 동시에 돌려도 안전하다")
    void deleteExpiredIsIdempotent() {
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(sha256Hex("expired-device"))
            .expiresAt(LocalDateTime.now().minusDays(1))
            .build());

        assertThat(refreshTokenStore.deleteExpired()).isEqualTo(1);
        assertThat(refreshTokenStore.deleteExpired()).isZero();
    }

    @Test
    @DisplayName("저장된 만료 시각은 토큰 자체의 만료보다 이르지 않다 — 정리가 살아 있는 토큰을 지우지 않는다")
    void storedExpiryIsNeverBeforeTokenExpiry() throws Exception {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());
        refreshTokenStore.save(user, token);

        // 서명 검증 없이 exp만 읽는다. 여기서 보려는 건 시각 비교뿐이다.
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        long expSeconds = new ObjectMapper().readTree(payload).get("exp").asLong();
        LocalDateTime tokenExpiry = LocalDateTime.ofInstant(Instant.ofEpochSecond(expSeconds), ZoneId.systemDefault());

        RefreshToken row = refreshTokenRepository.findAll().stream()
            .filter(candidate -> candidate.getTokenHash().equals(sha256Hex(token)))
            .findFirst()
            .orElseThrow();
        assertThat(row.getExpiresAt()).isAfterOrEqualTo(tokenExpiry);
    }
}
