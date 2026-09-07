package com.sago.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 서비스 회원 (FR-01). 소셜 로그인으로만 가입되며 비밀번호는 보관하지 않는다 —
 * 실제 인증 수단은 SocialAuth가 provider별로 들고 있다.
 *
 * 탈퇴는 hard delete가 아니라 deletedAt을 채우는 soft delete로 처리한다.
 * 사고 기록이 보험 처리 근거 자료라 회원이 나가더라도 함께 지워지면 안 되기 때문이다.
 */
@Entity
@Table(name = "\"user\"")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /**
     * 소셜에서 이메일 동의를 받지 못했을 때 채워 넣는 자리표시 주소의 도메인.
     * 실재하지 않는 도메인이라 이 주소로는 메일이 가지 않는다 —
     * 알림 발송이나 화면 노출 전에 hasPlaceholderEmail()로 걸러야 한다.
     */
    public static final String PLACEHOLDER_EMAIL_DOMAIN = "@social.sago";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "bike_model", length = 100)
    private String bikeModel;

    @Column(name = "bike_number", length = 20)
    private String bikeNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(String email, String nickname) {
        this.email = email;
        this.nickname = nickname;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /** 프로필 초기 설정·회원정보 수정에서 사용한다. */
    public void updateProfile(String nickname, String bikeModel, String bikeNumber) {
        this.nickname = nickname;
        this.bikeModel = bikeModel;
        this.bikeNumber = bikeNumber;
    }

    public void withdraw() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isWithdrawn() {
        return this.deletedAt != null;
    }

    /**
     * 이메일이 실제 주소가 아니라 가입 시 채워 넣은 자리표시 값인지 여부.
     *
     * 메일 발송이나 화면 노출 전에 이 값을 확인해야 한다. 자리표시 주소로 보낸 메일은
     * 오류 없이 조용히 사라지고, 화면에 그대로 뜨면 사용자가 자기 이메일로 오해한다.
     */
    public boolean hasPlaceholderEmail() {
        return this.email != null && this.email.endsWith(PLACEHOLDER_EMAIL_DOMAIN);
    }
}
