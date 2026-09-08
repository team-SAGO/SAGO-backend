package com.sago.domain.auth;

import com.sago.domain.user.User;
import com.sago.global.jwt.JwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 발급된 refresh 토큰을 보관하고 무효화한다.
 *
 * 저장소에 행이 있어야만 재발급이 되므로, 행을 지우는 것이 곧 토큰 무효화다.
 * 이 저장소가 생기기 전에는 서명과 만료만 확인해서, 로그아웃을 눌러도 이미 발급된 토큰이
 * 만료 전까지 계속 유효했다.
 */
@Component
public class RefreshTokenStore {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshExpiration;

    public RefreshTokenStore(RefreshTokenRepository refreshTokenRepository,
                             JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpiration = Duration.ofMillis(jwtProperties.getRefreshExpiration());
    }

    @Transactional
    public void save(User user, String refreshToken) {
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(hash(refreshToken))
            .expiresAt(LocalDateTime.now().plus(refreshExpiration))
            .build());
    }

    /**
     * 토큰을 무효화하고, 이번 호출이 실제로 무효화했는지 돌려준다.
     *
     * 확인과 삭제를 한 번의 원자적 연산으로 합친 것이 핵심이다. "있는지 보고 → 지운다"로 나누면
     * 같은 토큰으로 동시에 두 요청이 들어왔을 때 양쪽 다 확인을 통과해 각자 새 토큰을 받는다.
     * 그러면 회전의 핵심인 "한 번 쓴 토큰은 죽는다"는 보장이 깨진다. 액세스 토큰이 만료된 순간
     * 여러 요청이 함께 재발급을 시도하는 것은 앱에서 흔한 패턴이라 실제로 일어난다.
     *
     * 삭제된 행 수로 승자를 가리므로, true를 받은 쪽만 재발급을 이어가면 된다.
     *
     * @return 이번 호출로 무효화했으면 true, 이미 없던 토큰이면 false
     */
    @Transactional
    public boolean consume(String refreshToken) {
        return refreshTokenRepository.deleteByTokenHash(hash(refreshToken)) > 0;
    }

    /** 회원의 모든 토큰을 무효화한다. 회원 탈퇴 시 모든 기기에서 로그아웃시키기 위해 쓴다. */
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    /**
     * 토큰 원문 대신 저장할 해시를 만든다.
     *
     * BCrypt 같은 salt 기반 해시는 같은 입력이 매번 다른 값이 되어 저장된 값과 대조할 수 없다.
     * 그래서 조회가 가능한 SHA-256을 쓴다. 비밀번호와 달리 refresh 토큰은 서버가 만든
     * 고엔트로피 난수라 사전 공격 대상이 아니므로 이 선택이 성립한다.
     */
    private String hash(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                .formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 제공하도록 규격에 정해져 있어 실제로는 발생하지 않는다.
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
