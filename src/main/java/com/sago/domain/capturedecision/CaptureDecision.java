package com.sago.domain.capturedecision;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AR 촬영·Vision 태깅 진행 필요 여부 판단 결과 (Step 5, Prompt 3).
 */
@Entity
@Table(name = "capture_decision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaptureDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long decisionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accident_id", nullable = false)
    private Accident accident;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_by", nullable = false, length = 10)
    private DecidedBy decidedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public CaptureDecision(Accident accident, boolean required, String reason, DecidedBy decidedBy) {
        this.accident = accident;
        this.required = required;
        this.reason = reason;
        this.decidedBy = decidedBy;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
