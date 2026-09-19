package com.sago.domain.report;

import com.sago.domain.accident.Accident;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI가 생성한 사고 경위서 (Step 9, Prompt 6). narrative가 육하원칙 기반 본문이고,
 * summary·unverifiedItems는 사용자가 빠르게 검토할 수 있도록 뽑아낸 요약과 미확인 항목이다.
 *
 * pdfUrl은 이 경위서를 확정한 뒤 사고보고서(PDF)를 생성하는 단계에서 채워진다.
 */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accident_id", nullable = false)
    private Accident accident;

    @Column(name = "narrative", nullable = false, columnDefinition = "TEXT")
    private String narrative;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "report_summary", joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "line_no")
    @Column(name = "content", nullable = false, length = 255)
    private List<String> summary;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "report_unverified_item", joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "line_no")
    @Column(name = "content", nullable = false, length = 255)
    private List<String> unverifiedItems;

    @Column(name = "disclaimer", nullable = false, length = 255)
    private String disclaimer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReportStatus status;

    @Column(name = "version", nullable = false)
    private int version;

    // photo.file_url·statement.audio_file_url·user.profile_image_url과 같은 규칙 — S3Uploader가
    // 저장·삭제·presigned 발급 모두 URL에서 키를 꺼내 처리하므로, 여기도 전체 URL을 담고 _url로 부른다.
    @Column(name = "pdf_url", length = 512)
    private String pdfUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Report(Accident accident, String narrative, List<String> summary,
                   List<String> unverifiedItems, String disclaimer) {
        this.accident = accident;
        this.narrative = narrative;
        this.summary = summary;
        this.unverifiedItems = unverifiedItems;
        this.disclaimer = disclaimer;
        this.status = ReportStatus.DRAFT;
        this.version = 1;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
