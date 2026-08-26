package com.sago.domain.accident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사고 케이스. 사고 발생 버튼 클릭 시 생성되며(FR-02), 체크리스트·진술·사진·경위서 등
 * 사고 대응 플로우의 모든 산출물이 이 엔티티의 accidentId를 참조한다.
 *
 * userId는 User 엔티티가 아직 없어 원시 FK 컬럼으로 두었다. User 엔티티가 만들어지면
 * @ManyToOne 연관관계로 교체할 것.
 */
@Entity
@Table(name = "accident")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Accident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accident_id")
    private Long accidentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "accident_type", nullable = false, length = 20)
    private AccidentType accidentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "injury_self", length = 10)
    private InjuryLevel injurySelf;

    @Enumerated(EnumType.STRING)
    @Column(name = "injury_other", length = 10)
    private InjuryLevel injuryOther;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "direction", length = 50)
    private String direction;

    @Column(name = "road_condition", length = 100)
    private String roadCondition;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccidentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Accident(Long userId, AccidentType accidentType, InjuryLevel injurySelf,
                     InjuryLevel injuryOther, LocalDateTime occurredAt, BigDecimal latitude,
                     BigDecimal longitude, String direction, String roadCondition, String memo) {
        this.userId = userId;
        this.accidentType = accidentType;
        this.injurySelf = injurySelf;
        this.injuryOther = injuryOther;
        this.occurredAt = occurredAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.direction = direction;
        this.roadCondition = roadCondition;
        this.memo = memo;
        this.status = AccidentStatus.IN_PROGRESS;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void changeType(AccidentType accidentType, InjuryLevel injurySelf, InjuryLevel injuryOther) {
        this.accidentType = accidentType;
        this.injurySelf = injurySelf;
        this.injuryOther = injuryOther;
    }

    public void updateRecord(String direction, String roadCondition, String memo) {
        this.direction = direction;
        this.roadCondition = roadCondition;
        this.memo = memo;
    }

    public void complete() {
        this.status = AccidentStatus.COMPLETED;
    }
}
