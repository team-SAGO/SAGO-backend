package com.sago.domain.statement;

import com.sago.domain.accident.Accident;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사고 상황에 대한 음성 진술 (Step 4). 원본 음성 파일과 STT 변환 텍스트를 함께 보관한다.
 */
@Entity
@Table(name = "statement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "statement_id")
    private Long statementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accident_id", nullable = false)
    private Accident accident;

    @Column(name = "audio_file_url", nullable = false, length = 255)
    private String audioFileUrl;

    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Statement(Accident accident, String audioFileUrl, String sttText) {
        this.accident = accident;
        this.audioFileUrl = audioFileUrl;
        this.sttText = sttText;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void updateSttText(String sttText) {
        this.sttText = sttText;
    }
}
