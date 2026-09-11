package com.sago.domain.contact;

import com.sago.domain.accident.Accident;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사고 후 신고·보험사 연결을 시도한 기록 (FR-07).
 *
 * "연결에 성공했다"가 아니라 "연결을 시도했다"를 남긴다. 앱의 버튼은 전화 다이얼을 여는 것이라
 * 서버는 통화가 실제로 이어졌는지 알 수 없다. 경위서 등에 쓰일 때도 그렇게 읽혀야 한다.
 *
 * 같은 유형을 여러 번 눌러도 전부 기록한다. 첫 통화가 안 돼서 다시 건 것일 수 있어,
 * 기록의 목적("무엇을 언제 시도했는가")상 걸러내면 안 된다.
 */
@Entity
@Table(
    name = "contact_log",
    // 사고별로 시도한 순서대로 읽는다. 사고 기록과 함께 쌓이기만 하는 데이터라 미리 둔다.
    indexes = @Index(
        name = "idx_contact_log_accident_contacted",
        columnList = "accident_id, contacted_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_id")
    private Long contactId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accident_id", nullable = false)
    private Accident accident;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 20)
    private ContactType contactType;

    /**
     * 사용자가 버튼을 누른 시각. 서버가 요청을 받은 시각이 아니다 —
     * 현장 통신이 불안정하면 요청이 늦게 도착해, 받은 시각으로 남기면 실제보다 늦게 기록된다.
     */
    @Column(name = "contacted_at", nullable = false)
    private LocalDateTime contactedAt;

    @Builder
    public ContactLog(Accident accident, ContactType contactType, LocalDateTime contactedAt) {
        this.accident = accident;
        this.contactType = contactType;
        this.contactedAt = contactedAt;
    }
}
