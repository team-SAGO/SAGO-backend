package com.sago.domain.auth;

import com.sago.domain.user.User;
import com.sago.global.jwt.JwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final long refreshExpirationMillis;

    public RefreshTokenStore(RefreshTokenRepository refreshTokenRepository,
                             JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationMillis = jwtProperties.getRefreshExpiration();
    }

    @Transactional
    public void save(User user, String refreshToken) {
        refreshTokenRepository.save(RefreshToken.builder()
            .user(user)
            .tokenHash(hash(refreshToken))
            .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMillis * 1_000_000))
            .build());
    }

    /**
     * 저장된 토큰인지 확인한다. 서명·만료가 멀쩡해도 여기에 없으면 이미 무효화된 토큰이다
     * (로그아웃했거나, 재발급에 한 번 쓰였거나, 회원이 탈퇴한 경우).
     */
    @Transactional(readOnly = true)
    public boolean isStored(String refreshToken) {
        return refreshTokenRepository.existsByTokenHash(hash(refreshToken));
    }

    /**
     * 토큰 하나를 무효화한다. 이미 없어도 조용히 넘어간다 —
     * 로그아웃을 두 번 눌렀다고 오류를 낼 이유가 없다.
     */
    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
    }

    /** 회원의 모든 토큰을 무효화한다. 회원 탈퇴 시 모든 기기에서 로그아웃시키기 위해 쓴다. */
    @Transactional
    public void revokeAll(Long userId) {
        refreshTokenRepository.deleteByUser_UserId(userId);
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
