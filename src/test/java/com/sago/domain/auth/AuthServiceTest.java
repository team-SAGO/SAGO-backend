package com.sago.domain.auth;

import com.sago.domain.auth.dto.LoginResponse;
import com.sago.domain.user.AuthProvider;
import com.sago.domain.user.SocialAuth;
import com.sago.domain.user.SocialAuthRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 소셜 로그인 분기(신규 가입 / 기존 회원 / 탈퇴 회원)와 토큰 재발급 동작을 검증한다.
 * 외부 소셜 API는 고정된 사용자 정보를 돌려주는 가짜 클라이언트로 대체한다.
 */
class AuthServiceTest {

    private static final String CODE = "dummy-authorization-code";
    private static final OAuthUserInfo KAKAO_USER =
        new OAuthUserInfo("kakao-1234", "rider@example.com", "라이더");

    private UserRepository userRepository;
    private SocialAuthRepository socialAuthRepository;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    /** save()가 실제 DB처럼 id를 채워주도록 하는 간단한 인메모리 대역. */
    private final Map<Long, User> savedUsers = new HashMap<>();
    private final AtomicLong userIdSequence = new AtomicLong();

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        socialAuthRepository = mock(SocialAuthRepository.class);

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-only-secret-key-for-sago-backend-1234567890");
        jwtProperties.setAccessExpiration(3_600_000L);
        jwtProperties.setRefreshExpiration(1_209_600_000L);
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setUserId(user, userIdSequence.incrementAndGet());
            savedUsers.put(user.getUserId(), user);
            return user;
        });
        when(socialAuthRepository.saveAndFlush(any(SocialAuth.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        OAuthClient kakaoClient = new FakeOAuthClient(AuthProvider.KAKAO, KAKAO_USER);
        authService = new AuthService(List.of(kakaoClient), userRepository, socialAuthRepository, jwtTokenProvider);
    }

    @Test
    @DisplayName("처음 보는 소셜 계정이면 회원을 새로 만들고 newUser=true로 알려준다")
    void firstLoginRegistersUser() {
        when(socialAuthRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-1234"))
            .thenReturn(Optional.empty());

        LoginResponse response = authService.login(AuthProvider.KAKAO, CODE);

        assertThat(response.newUser()).isTrue();
        assertThat(savedUsers).hasSize(1);
        assertThat(savedUsers.values().iterator().next().getEmail()).isEqualTo("rider@example.com");
        assertThat(jwtTokenProvider.parseUserId(response.token().accessToken(), TokenType.ACCESS))
            .isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 연결된 소셜 계정이면 회원을 새로 만들지 않는다")
    void repeatedLoginReusesExistingUser() {
        User existing = User.builder().email("rider@example.com").nickname("라이더").build();
        setUserId(existing, 7L);
        when(socialAuthRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-1234"))
            .thenReturn(Optional.of(SocialAuth.builder()
                .user(existing)
                .provider(AuthProvider.KAKAO)
                .providerUserId("kakao-1234")
                .build()));

        LoginResponse response = authService.login(AuthProvider.KAKAO, CODE);

        assertThat(response.newUser()).isFalse();
        assertThat(savedUsers).isEmpty();
        assertThat(jwtTokenProvider.parseUserId(response.token().accessToken(), TokenType.ACCESS))
            .isEqualTo(7L);
    }

    @Test
    @DisplayName("탈퇴한 회원은 다시 로그인할 수 없다")
    void withdrawnUserCannotLogin() {
        User withdrawn = User.builder().email("rider@example.com").nickname("라이더").build();
        setUserId(withdrawn, 7L);
        withdrawn.withdraw();
        when(socialAuthRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-1234"))
            .thenReturn(Optional.of(SocialAuth.builder()
                .user(withdrawn)
                .provider(AuthProvider.KAKAO)
                .providerUserId("kakao-1234")
                .build()));

        assertThatThrownBy(() -> authService.login(AuthProvider.KAKAO, CODE))
            .isInstanceOf(WithdrawnUserException.class);
    }

    @Test
    @DisplayName("이메일 동의를 받지 못한 소셜 계정도 가입이 되도록 대체 주소를 채운다")
    void registersEvenWhenProviderGivesNoEmail() {
        OAuthClient noEmailClient = new FakeOAuthClient(
            AuthProvider.KAKAO, new OAuthUserInfo("kakao-9999", null, null));
        AuthService service = new AuthService(
            List.of(noEmailClient), userRepository, socialAuthRepository, jwtTokenProvider);
        when(socialAuthRepository.findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-9999"))
            .thenReturn(Optional.empty());

        service.login(AuthProvider.KAKAO, CODE);

        assertThat(savedUsers.values().iterator().next().getEmail())
            .isEqualTo("kakao_kakao-9999@social.sago");
    }

    @Test
    @DisplayName("등록되지 않은 제공자로 로그인하면 거부된다")
    void unsupportedProviderIsRejected() {
        assertThatThrownBy(() -> authService.login(AuthProvider.GOOGLE, CODE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("refresh 토큰으로 새 토큰을 재발급받는다")
    void reissueWithRefreshToken() {
        User user = User.builder().email("rider@example.com").nickname("라이더").build();
        setUserId(user, 7L);
        when(userRepository.findByUserIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

        String refreshToken = jwtTokenProvider.createRefreshToken(7L);

        assertThat(jwtTokenProvider.parseUserId(authService.reissue(refreshToken).accessToken(), TokenType.ACCESS))
            .isEqualTo(7L);
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
    private void setUserId(User user, Long userId) {
        try {
            var field = User.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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
