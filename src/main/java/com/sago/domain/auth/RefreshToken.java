package com.sago.domain.auth;

import com.sago.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발급되어 아직 유효한 refresh 토큰.
 *
 * 이 테이블에 행이 있어야만 재발급이 되므로, 행을 지우는 것이 곧 토큰 무효화다.
 * 로그아웃은 해당 행을, 회원 탈퇴는 그 회원의 행 전체를 지운다.
 *
 * 토큰 원문이 아니라 해시를 저장한다. DB가 유출돼도 그 값으로 바로 로그인할 수 없게 하려는 것이다.
 * 한 회원이 여러 기기에서 로그인할 수 있으므로 User와 1:N이다.
 */
@Entity
@Table(
    name = "refresh_token",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_refresh_token_hash",
        columnNames = "token_hash")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 토큰 원문의 SHA-256 해시(16진수). 원문은 어디에도 저장하지 않는다. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /**
     * 토큰 자체의 만료 시각. JWT에도 만료가 들어 있어 중복이지만,
     * 만료된 행을 나중에 일괄 정리할 때 토큰을 파싱하지 않고 걸러내기 위해 함께 둔다.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
