package com.sago.domain.terms;

import com.sago.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 약관 동의 이력 (FR-01).
 *
 * 같은 약관에 다시 동의하더라도 기존 행을 고치지 않고 새 행을 쌓는다.
 * "언제 어느 버전에 동의했는가"가 법적 근거로 쓰이는 값이라 과거 기록이 덮여서는 안 된다.
 * 현재 동의 상태는 유형별 최신 행으로 판단한다.
 *
 * 마케팅 수신처럼 철회할 수 있는 항목도 같은 구조로 다룬다 — 철회는 agreed=false인 새 행이다.
 */
@Entity
@Table(
    name = "terms_agreement",
    // 이력을 쌓는 테이블이라 행이 계속 늘어난다. 현재 동의 상태를 볼 때마다 회원의 유형별
    // 최신 행을 찾으므로, 인덱스가 없으면 회원 수가 늘수록 그 조회가 느려진다.
    indexes = @Index(
        name = "idx_terms_agreement_user_type",
        columnList = "user_id, terms_type")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agreement_id")
    private Long agreementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", nullable = false, length = 20)
    private TermsType termsType;

    @Column(name = "is_agreed", nullable = false)
    private boolean agreed;

    /**
     * 동의 시점의 약관 버전. 약관이 개정되면 terms.yml의 버전이 올라가고, 이 값과 달라진다.
     * 그 차이로 재동의가 필요한지 판단하므로 동의 당시 값을 그대로 남긴다.
     */
    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private LocalDateTime agreedAt;

    @Builder
    public TermsAgreement(User user, TermsType termsType, boolean agreed, String version) {
        this.user = user;
        this.termsType = termsType;
        this.agreed = agreed;
        this.version = version;
    }

    @PrePersist
    private void prePersist() {
        this.agreedAt = LocalDateTime.now();
    }
}
