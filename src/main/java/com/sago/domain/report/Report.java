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
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "report",
    // 사고 하나에 경위서는 한 건이다 (#86). 재생성·수정은 이 행을 갈아끼우며 version을 올린다.
    // 제약을 DB에도 두는 것은, 동시에 두 번 생성 요청이 와도 두 건이 남지 않게 하기 위해서다.
    uniqueConstraints = @UniqueConstraint(name = "uk_report_accident", columnNames = "accident_id")
)
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

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

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

    /**
     * 다시 생성한 내용으로 갈아끼운다 (#86).
     *
     * 사고당 경위서를 한 건으로 두고 version으로 몇 번째 내용인지 센다. 행을 여러 개 쌓으면
     * "어느 것을 확정할지", "어느 것을 PDF로 만들지"가 화면마다 따라붙는데, 사용자에게 필요한 것은
     * 언제나 "지금 경위서"다.
     */
    public void replaceContent(String narrative, List<String> summary,
                                List<String> unverifiedItems, String disclaimer) {
        this.narrative = narrative;
        this.summary = summary;
        this.unverifiedItems = unverifiedItems;
        this.disclaimer = disclaimer;
        this.version++;
    }

    /** 사용자가 본문을 고친다. 요약·미확인 항목은 AI가 뽑은 그대로 둔다. */
    public void updateNarrative(String narrative) {
        this.narrative = narrative;
        this.version++;
    }

    /**
     * 확정. 이후에는 내용을 바꿀 수 없다 — 보험 서류로 나간 문서가 나중에 달라지면 안 된다.
     * 확정 시각을 남기는 것은 경위서가 언제 기준의 문서인지 서류에 드러나야 하기 때문이다.
     */
    public void confirm() {
        this.status = ReportStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public boolean isConfirmed() {
        return this.status == ReportStatus.CONFIRMED;
    }
}
