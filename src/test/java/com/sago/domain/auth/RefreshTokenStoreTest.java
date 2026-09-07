package com.sago.domain.auth;

import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    @DisplayName("저장한 토큰은 저장소에서 확인된다")
    void savedTokenIsFound() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());

        refreshTokenStore.save(user, token);

        assertThat(refreshTokenStore.isStored(token)).isTrue();
    }

    @Test
    @DisplayName("저장한 적 없는 토큰은 확인되지 않는다")
    void unknownTokenIsNotFound() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());

        assertThat(refreshTokenStore.isStored(token)).isFalse();
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
    @DisplayName("무효화한 토큰은 더 이상 확인되지 않는다")
    void revokedTokenIsGone() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());
        refreshTokenStore.save(user, token);

        refreshTokenStore.revoke(token);

        assertThat(refreshTokenStore.isStored(token)).isFalse();
    }

    @Test
    @DisplayName("이미 없는 토큰을 무효화해도 오류가 나지 않는다")
    void revokingUnknownTokenIsSilent() {
        String token = jwtTokenProvider.createRefreshToken(user.getUserId());

        refreshTokenStore.revoke(token);

        assertThat(refreshTokenStore.isStored(token)).isFalse();
    }

    @Test
    @DisplayName("회원의 토큰을 전부 무효화하면 다른 기기 토큰도 함께 사라진다")
    void revokeAllRemovesEveryDeviceToken() {
        String phone = jwtTokenProvider.createRefreshToken(user.getUserId());
        String tablet = jwtTokenProvider.createRefreshToken(user.getUserId()) + "-other-device";
        refreshTokenStore.save(user, phone);
        refreshTokenStore.save(user, tablet);

        refreshTokenStore.revokeAll(user.getUserId());

        assertThat(refreshTokenStore.isStored(phone)).isFalse();
        assertThat(refreshTokenStore.isStored(tablet)).isFalse();
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

        assertThat(refreshTokenStore.isStored(mine)).isFalse();
        assertThat(refreshTokenStore.isStored(theirs)).isTrue();
    }
}
