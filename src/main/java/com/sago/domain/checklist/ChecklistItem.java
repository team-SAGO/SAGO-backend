package com.sago.domain.checklist;

import com.sago.domain.accident.Accident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사고 유형별 AI 대응 체크리스트 항목 (Step 3). accident에 종속되며,
 * source로 AI 생성인지 정적 폴백인지 구분한다.
 */
@Entity
@Table(name = "checklist_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "checklist_item_id")
    private Long checklistItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accident_id", nullable = false)
    private Accident accident;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    @Column(name = "order_no", nullable = false)
    private Integer orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private ChecklistSource source;

    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    public ChecklistItem(Accident accident, String content, Integer orderNo, ChecklistSource source) {
        this.accident = accident;
        this.content = content;
        this.orderNo = orderNo;
        this.source = source;
        this.completed = false;
    }

    public void complete() {
        this.completed = true;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 완료 표시를 해제한다.
     *
     * 사고 직후 급한 상황에서 누르는 화면이라 잘못 체크하는 일이 생긴다.
     * 되돌릴 수 없으면 사용자가 완료하지 않은 항목을 완료로 남긴 채 다음 단계로 넘어가게 된다.
     */
    public void uncomplete() {
        this.completed = false;
        this.completedAt = null;
    }

    /** 소속 사고가 맞는지 확인한다. 다른 사고의 항목을 지정해 수정하는 것을 막는 데 쓴다. */
    public boolean belongsTo(Long accidentId) {
        return this.accident != null && this.accident.getAccidentId().equals(accidentId);
    }
}
