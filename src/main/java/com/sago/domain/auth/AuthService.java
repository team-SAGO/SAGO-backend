package com.sago.domain.auth;

import com.sago.domain.auth.dto.LoginResponse;
import com.sago.domain.auth.dto.TokenResponse;
import com.sago.domain.user.AuthProvider;
import com.sago.domain.user.User;
import com.sago.domain.user.UserRepository;
import com.sago.global.client.oauth.OAuthClient;
import com.sago.global.client.oauth.OAuthUserInfo;
import com.sago.global.jwt.InvalidTokenException;
import com.sago.global.jwt.JwtTokenProvider;
import com.sago.global.jwt.TokenType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Step 1 — 소셜 로그인/회원가입 (FR-01).
 *
 * 소셜 인가 코드를 받아 사용자 정보를 조회하고, 처음 보는 소셜 계정이면 회원을 만든 뒤
 * 서비스 자체 JWT를 발급한다. 별도의 회원가입 API를 두지 않고 최초 로그인이 곧 가입이다.
 *
 * login()에는 트랜잭션을 걸지 않는다. 소셜 서버 호출이 느릴 때 그 시간만큼 DB 커넥션을
 * 붙잡지 않기 위해서다. DB 작업은 SocialAccountRegistrar가 짧은 트랜잭션으로 나눠 처리한다.
 */
@Service
public class AuthService {

    private final Map<AuthProvider, OAuthClient> oAuthClients = new EnumMap<>(AuthProvider.class);
    private final SocialAccountRegistrar socialAccountRegistrar;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(List<OAuthClient> oAuthClients,
                       SocialAccountRegistrar socialAccountRegistrar,
                       RefreshTokenStore refreshTokenStore,
                       UserRepository userRepository,
                       JwtTokenProvider jwtTokenProvider) {
        oAuthClients.forEach(client -> this.oAuthClients.put(client.getProvider(), client));
        this.socialAccountRegistrar = socialAccountRegistrar;
        this.refreshTokenStore = refreshTokenStore;
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(AuthProvider provider, String authorizationCode) {
        OAuthClient client = oAuthClients.get(provider);
        if (client == null) {
            throw new UnsupportedProviderException("지원하지 않는 소셜 로그인입니다: " + provider);
        }

        // 외부 HTTP 호출. 트랜잭션 밖에서 끝낸다.
        OAuthUserInfo userInfo = client.fetchUserInfo(authorizationCode);

        Optional<User> existing =
            socialAccountRegistrar.findUser(provider, userInfo.providerUserId());

        boolean newUser = existing.isEmpty();
        User user = existing.orElseGet(() -> registerOrRecover(provider, userInfo));

        // 탈퇴 회원은 로그인을 막는다. soft delete라 사고 기록이 남아 있어 그냥 통과시키면
        // 탈퇴한 계정으로 기존 데이터에 다시 접근하게 된다. 복구 정책은 팀 논의 후 정할 것.
        if (user.isWithdrawn()) {
            throw new WithdrawnUserException("탈퇴한 회원입니다. 고객센터를 통해 복구를 요청해주세요.");
        }

        return new LoginResponse(issueTokens(user), newUser);
    }

    /**
     * 로그아웃. 넘겨받은 refresh 토큰만 무효화하므로 다른 기기의 로그인은 유지된다.
     * 이미 무효화된 토큰이어도 성공으로 본다 — 로그아웃을 두 번 눌렀다고 오류를 낼 이유가 없다.
     */
    public void logout(String refreshToken) {
        refreshTokenStore.consume(refreshToken);
    }

    /**
     * 같은 소셜 계정으로 동시에 두 번 로그인이 들어오면 유니크 제약에 걸린다.
     * 이때는 먼저 커밋된 쪽을 정답으로 보고 새 트랜잭션에서 다시 조회한다 —
     * 등록 트랜잭션은 이미 롤백되었으므로 재조회는 깨끗한 상태에서 이뤄진다.
     */
    private User registerOrRecover(AuthProvider provider, OAuthUserInfo userInfo) {
        try {
            return socialAccountRegistrar.register(provider, userInfo);
        } catch (DataIntegrityViolationException e) {
            return socialAccountRegistrar.findUser(provider, userInfo.providerUserId())
                .orElseThrow(() -> e);
        }
    }

    /**
     * Refresh 토큰으로 Access 토큰을 재발급한다.
     *
     * 서명·만료뿐 아니라 저장소에 남아 있는 토큰인지도 확인한다. 서명이 멀쩡해도 로그아웃했거나
     * 이미 재발급에 쓰인 토큰이면 저장소에 없으므로 거부된다.
     *
     * 확인과 무효화를 consume() 한 번으로 합친다(회전). 나눠서 하면 같은 토큰으로 동시에 두
     * 요청이 들어왔을 때 양쪽 다 통과해 각자 새 토큰을 받고, "한 번 쓴 토큰은 죽는다"는 보장이
     * 깨진다. 토큰이 유출됐을 때 정상 사용자와 공격자가 같은 토큰으로 각각 재발급받는 것도
     * 막지 못한다.
     */
    public TokenResponse reissue(String refreshToken) {
        Long userId = jwtTokenProvider.parseUserId(refreshToken, TokenType.REFRESH);

        if (!refreshTokenStore.consume(refreshToken)) {
            throw new InvalidTokenException("이미 사용되었거나 무효화된 토큰입니다.");
        }

        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new InvalidTokenException("존재하지 않거나 탈퇴한 회원의 토큰입니다."));

        return issueTokens(user);
    }

    /**
     * 토큰 한 쌍을 발급하고 refresh 토큰을 저장소에 남긴다.
     * 저장에 실패하면 재발급이 안 되는 토큰을 쥐여주는 셈이라, 저장까지 끝난 뒤에 응답을 만든다.
     */
    private TokenResponse issueTokens(User user) {
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());
        refreshTokenStore.save(user, refreshToken);

        return new TokenResponse(
            jwtTokenProvider.createAccessToken(user.getUserId()),
            refreshToken,
            jwtTokenProvider.getAccessExpirationSeconds()
        );
    }
}
