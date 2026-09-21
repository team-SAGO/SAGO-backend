package com.sago.domain.report.dto;

import com.sago.domain.report.Report;
import com.sago.domain.report.ReportStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경위서 한 건.
 *
 * PDF 주소는 내보내지 않는다. 사진·진술과 같은 이유로 비공개 버킷 주소라 열리지 않고 오브젝트 키만
 * 드러난다. 대신 PDF가 준비됐는지만 알려주고, 내려받기는 만료 시간이 있는 주소를 따로 발급해 처리한다.
 *
 * @param version     내용이 몇 번 바뀌었는지. 재생성·수정할 때마다 올라간다.
 * @param confirmedAt 확정 시각. 확정 전에는 null이다.
 */
public record ReportResponse(
    Long reportId,
    String narrative,
    List<String> summary,
    List<String> unverifiedItems,
    String disclaimer,
    ReportStatus status,
    int version,
    boolean pdfReady,
    LocalDateTime confirmedAt,
    LocalDateTime createdAt
) {

    /** 지연 로딩 필드(요약·미확인 항목)를 건드리므로 트랜잭션 안에서 불러야 한다. */
    public static ReportResponse from(Report report) {
        return new ReportResponse(
            report.getReportId(),
            report.getNarrative(),
            List.copyOf(report.getSummary()),
            List.copyOf(report.getUnverifiedItems()),
            report.getDisclaimer(),
            report.getStatus(),
            report.getVersion(),
            report.getPdfUrl() != null,
            report.getConfirmedAt(),
            report.getCreatedAt()
        );
    }
}
