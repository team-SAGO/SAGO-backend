package com.sago.domain.supplementquestion;

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
 * 육하원칙 기준 누락 정보를 보완하기 위한 AI 질문 (Step 4, Prompt 2).
 * 사용자가 답변하면 answer에 채워진다.
 */
@Entity
@Table(name = "supplement_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplementQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accident_id", nullable = false)
    private Accident accident;

    @Column(name = "question", nullable = false, length = 255)
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "round", nullable = false, length = 10)
    private QuestionRound round;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SupplementQuestion(Accident accident, String question, QuestionRound round) {
        this.accident = accident;
        this.question = question;
        this.round = round;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void answer(String answer) {
        this.answer = answer;
    }
}
