package com.sago.domain.auth;

import com.sago.domain.auth.dto.LoginResponse;
import com.sago.domain.auth.dto.TokenResponse;
import com.sago.domain.user.AuthProvider;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.client.oauth.OAuthClient;
import com.sago.global.client.oauth.OAuthUserInfo;
import com.sago.global.jwt.InvalidTokenException;
import com.sago.global.jwt.JwtProperties;
import com.sago.global.jwt.JwtTokenProvider;
import com.sago.global.jwt.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 로그인 분기(신규 가입 / 기존 회원 / 탈퇴 회원)와 토큰 재발급을 검증한다.
 * 외부 소셜 API는 고정된 사용자 정보를 돌려주는 가짜 클라이언트로 대체한다.
 *
 * 등록 시 유니크 제약 위반이 실제로 복구되는지는 트랜잭션 동작에 달린 문제라
 * 여기서는 확인할 수 없다 — SocialAccountRegistrarTest에서 실제 DB로 검증한다.
 */
class AuthServiceTest {

    private static final String CODE = "dummy-authorization-code";
    private static final OAuthUserInfo KAKAO_USER =
        new OAuthUserInfo("kakao-1234", "rider@example.com", "라이더");

    private SocialAccountRegistrar registrar;
    private RefreshTokenStore refreshTokenStore;
    private UserRepository userRepository;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        registrar = mock(SocialAccountRegistrar.class);
        refreshTokenStore = mock(RefreshTokenStore.class);
        userRepository = mock(UserRepository.class);

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-only-secret-key-for-sago-backend-1234567890");
        jwtProperties.setAccessExpiration(3_600_000L);
        jwtProperties.setRefreshExpiration(1_209_600_000L);
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);

        OAuthClient kakaoClient = new FakeOAuthClient(AuthProvider.KAKAO, KAKAO_USER);
        authService = new AuthService(
            List.of(kakaoClient), registrar, refreshTokenStore, userRepository, jwtTokenProvider);
        // 저장소에 남아 있는 토큰인지 확인하는 단계는 기본적으로 통과시킨다.
        when(refreshTokenStore.isStored(any())).thenReturn(true);
    }

    @Test
    @DisplayName("처음 보는 소셜 계정이면 회원을 등록하고 newUser=true로 알려준다")
    void firstLoginRegistersUser() {
        when(registrar.findUser(AuthProvider.KAKAO, "kakao-1234")).thenReturn(Optional.empty());
        when(registrar.register(eq(AuthProvider.KAKAO), any())).thenReturn(user(1L));

        LoginResponse response = authService.login(AuthProvider.KAKAO, CODE);

        assertThat(response.newUser()).isTrue();
        assertThat(jwtTokenProvider.parseUserId(response.token().accessToken(), TokenType.ACCESS))
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 연결된 소셜 계정이면 회원을 새로 만들지 않는다")
    void repeatedLoginReusesExistingUser() {
        when(registrar.findUser(AuthProvider.KAKAO, "kakao-1234"))
            .thenReturn(Optional.of(user(7L)));

        LoginResponse response = authService.login(AuthProvider.KAKAO, CODE);

        assertThat(response.newUser()).isFalse();
        verify(registrar, never()).register(any(), any());
        assertThat(jwtTokenProvider.parseUserId(response.token().accessToken(), TokenType.ACCESS))
            .isEqualTo(7L);
    }

    @Test
    @DisplayName("등록 중 제약 위반이 나면 먼저 커밋된 회원으로 로그인시킨다")
    void duplicateRegistrationFallsBackToExistingUser() {
        when(registrar.findUser(AuthProvider.KAKAO, "kakao-1234"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(user(7L)));
        when(registrar.register(eq(AuthProvider.KAKAO), any()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        LoginResponse response = authService.login(AuthProvider.KAKAO, CODE);

        assertThat(jwtTokenProvider.parseUserId(response.token().accessToken(), TokenType.ACCESS))
            .isEqualTo(7L);
    }

    @Test
    @DisplayName("제약 위반 후에도 회원을 못 찾으면 원래 예외를 그대로 올린다")
    void unrecoverableConstraintViolationIsRethrown() {
        when(registrar.findUser(AuthProvider.KAKAO, "kakao-1234")).thenReturn(Optional.empty());
        when(registrar.register(eq(AuthProvider.KAKAO), any()))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> authService.login(AuthProvider.KAKAO, CODE))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("탈퇴한 회원은 다시 로그인할 수 없다")
    void withdrawnUserCannotLogin() {
        User withdrawn = user(7L);
        withdrawn.withdraw();
        when(registrar.findUser(AuthProvider.KAKAO, "kakao-1234"))
            .thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> authService.login(AuthProvider.KAKAO, CODE))
            .isInstanceOf(WithdrawnUserException.class);
    }

    @Test
    @DisplayName("등록되지 않은 제공자로 로그인하면 거부된다")
    void unsupportedProviderIsRejected() {
        assertThatThrownBy(() -> authService.login(AuthProvider.GOOGLE, CODE))
            .isInstanceOf(UnsupportedProviderException.class);
    }

    @Test
    @DisplayName("refresh 토큰으로 새 토큰을 재발급받는다")
    void reissueWithRefreshToken() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user(7L)));

        String refreshToken = jwtTokenProvider.createRefreshToken(7L);

        assertThat(jwtTokenProvider.parseUserId(
            authService.reissue(refreshToken).accessToken(), TokenType.ACCESS)).isEqualTo(7L);
    }

    @Test
    @DisplayName("저장소에 없는 refresh 토큰은 거부된다")
    void reissueRejectsRevokedToken() {
        when(refreshTokenStore.isStored(any())).thenReturn(false);

        String refreshToken = jwtTokenProvider.createRefreshToken(7L);

        assertThatThrownBy(() -> authService.reissue(refreshToken))
            .isInstanceOf(InvalidTokenException.class)
            .hasMessageContaining("무효화된");
    }

    @Test
    @DisplayName("재발급하면 쓴 토큰은 버리고 새 토큰을 저장한다")
    void reissueRotatesToken() {
        User user = user(7L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

        String oldToken = jwtTokenProvider.createRefreshToken(7L);
        TokenResponse response = authService.reissue(oldToken);

        verify(refreshTokenStore).revoke(oldToken);
        verify(refreshTokenStore).save(user, response.refreshToken());
    }

    @Test
    @DisplayName("로그인하면 발급된 refresh 토큰이 저장된다")
    void loginStoresRefreshToken() {
        User user = user(7L);
        when(registrar.findUser(AuthProvider.KAKAO, "kakao-1234")).thenReturn(Optional.of(user));

        LoginResponse response = authService.login(AuthProvider.KAKAO, CODE);

        verify(refreshTokenStore).save(user, response.token().refreshToken());
    }

    @Test
    @DisplayName("로그아웃하면 넘겨받은 토큰만 무효화된다")
    void logoutRevokesOnlyGivenToken() {
        String refreshToken = jwtTokenProvider.createRefreshToken(7L);

        authService.logout(refreshToken);

        verify(refreshTokenStore).revoke(refreshToken);
        verify(refreshTokenStore, never()).revokeAll(any());
    }

    @Test
    @DisplayName("access 토큰으로는 재발급받을 수 없다")
    void reissueRejectsAccessToken() {
        String accessToken = jwtTokenProvider.createAccessToken(7L);

        assertThatThrownBy(() -> authService.reissue(accessToken))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("탈퇴한 회원의 refresh 토큰은 재발급되지 않는다")
    void reissueRejectsWithdrawnUser() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

        String refreshToken = jwtTokenProvider.createRefreshToken(7L);

        assertThatThrownBy(() -> authService.reissue(refreshToken))
            .isInstanceOf(InvalidTokenException.class);
    }

    /** userId는 DB가 채우는 값이라 테스트에서는 리플렉션으로 직접 넣는다. */
    private User user(Long userId) {
        User user = User.builder().email("rider@example.com").nickname("라이더").build();
        try {
            var field = User.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }

    private record FakeOAuthClient(AuthProvider provider, OAuthUserInfo userInfo) implements OAuthClient {

        @Override
        public AuthProvider getProvider() {
            return provider;
        }

        @Override
        public OAuthUserInfo fetchUserInfo(String authorizationCode) {
            return userInfo;
        }
    }
}
