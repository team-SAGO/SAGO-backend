package com.sago.domain.auth;

import com.sago.domain.auth.dto.LoginResponse;
import com.sago.domain.auth.dto.TokenResponse;
import com.sago.domain.user.AuthProvider;
import com.sago.domain.user.SocialAuth;
import com.sago.domain.user.SocialAuthRepository;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.client.oauth.OAuthClient;
import com.sago.global.client.oauth.OAuthUserInfo;
import com.sago.global.jwt.InvalidTokenException;
import com.sago.global.jwt.JwtTokenProvider;
import com.sago.global.jwt.TokenType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Step 1 — 소셜 로그인/회원가입 (FR-01).
 *
 * 소셜 인가 코드를 받아 사용자 정보를 조회하고, 처음 보는 소셜 계정이면 회원을 만든 뒤
 * 서비스 자체 JWT를 발급한다. 별도의 회원가입 API를 두지 않고 최초 로그인이 곧 가입이다.
 */
@Service
public class AuthService {

    private final Map<AuthProvider, OAuthClient> oAuthClients = new EnumMap<>(AuthProvider.class);
    private final UserRepository userRepository;
    private final SocialAuthRepository socialAuthRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(List<OAuthClient> oAuthClients,
                       UserRepository userRepository,
                       SocialAuthRepository socialAuthRepository,
                       JwtTokenProvider jwtTokenProvider) {
        oAuthClients.forEach(client -> this.oAuthClients.put(client.getProvider(), client));
        this.userRepository = userRepository;
        this.socialAuthRepository = socialAuthRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public LoginResponse login(AuthProvider provider, String authorizationCode) {
        OAuthClient client = oAuthClients.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        OAuthUserInfo userInfo = client.fetchUserInfo(authorizationCode);

        SocialAuth socialAuth = socialAuthRepository
            .findByProviderAndProviderUserId(provider, userInfo.providerUserId())
            .orElse(null);

        boolean newUser = (socialAuth == null);
        User user = newUser ? register(provider, userInfo) : socialAuth.getUser();

        // 탈퇴 회원은 로그인을 막는다. soft delete라 사고 기록이 남아 있어 그냥 통과시키면
        // 탈퇴한 계정으로 기존 데이터에 다시 접근하게 된다. 복구 정책은 팀 논의 후 정할 것.
        if (user.isWithdrawn()) {
            throw new WithdrawnUserException("탈퇴한 회원입니다. 고객센터를 통해 복구를 요청해주세요.");
        }

        return new LoginResponse(issueTokens(user.getUserId()), newUser);
    }

    /**
     * 최초 로그인 시 회원과 소셜 연결 정보를 함께 만든다.
     *
     * 유니크 제약 위반은 같은 소셜 계정으로 동시에 두 번 로그인이 들어온 경우다.
     * 이때는 먼저 커밋된 쪽을 정답으로 보고 그 회원을 다시 조회해 돌려준다.
     */
    private User register(AuthProvider provider, OAuthUserInfo userInfo) {
        User user = userRepository.save(User.builder()
            .email(resolveEmail(provider, userInfo))
            .nickname(userInfo.nickname())
            .build());

        try {
            socialAuthRepository.saveAndFlush(SocialAuth.builder()
                .user(user)
                .provider(provider)
                .providerUserId(userInfo.providerUserId())
                .build());
        } catch (DataIntegrityViolationException e) {
            return socialAuthRepository
                .findByProviderAndProviderUserId(provider, userInfo.providerUserId())
                .orElseThrow(() -> e)
                .getUser();
        }

        return user;
    }

    /**
     * 카카오는 이메일이 선택 동의 항목이라 내려오지 않을 수 있다.
     * email 컬럼이 NOT NULL이므로 제공자 식별자를 이용한 자리표시 주소를 채워두고,
     * 이후 프로필 설정에서 실제 주소를 받는다.
     */
    private String resolveEmail(AuthProvider provider, OAuthUserInfo userInfo) {
        if (userInfo.email() != null && !userInfo.email().isBlank()) {
            return userInfo.email();
        }
        return provider.name().toLowerCase() + "_" + userInfo.providerUserId() + "@social.sago";
    }

    /**
     * Refresh 토큰으로 Access 토큰을 재발급한다.
     *
     * TODO: 현재는 서명과 만료만 확인하는 무상태 방식이라, 발급된 refresh 토큰은 만료 전까지 계속 유효하다.
     *       로그아웃(설정 화면)에서 토큰을 즉시 무효화하려면 refresh 토큰 저장소가 필요하다 — 해당 이슈에서 추가할 것.
     */
    @Transactional(readOnly = true)
    public TokenResponse reissue(String refreshToken) {
        Long userId = jwtTokenProvider.parseUserId(refreshToken, TokenType.REFRESH);

        userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new InvalidTokenException("존재하지 않거나 탈퇴한 회원의 토큰입니다."));

        return issueTokens(userId);
    }

    private TokenResponse issueTokens(Long userId) {
        return new TokenResponse(
            jwtTokenProvider.createAccessToken(userId),
            jwtTokenProvider.createRefreshToken(userId),
            jwtTokenProvider.getAccessExpirationSeconds()
        );
    }
}
