package com.sago.domain.report;

import com.sago.domain.accident.Accident;
import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.S3CommunicationException;
import com.sago.global.client.s3.S3Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportPdfServiceTest {

    private static final String STORED_URL =
        "https://sago.s3.ap-northeast-2.amazonaws.com/reports/abc.pdf";

    private S3Uploader s3Uploader;
    private ReportPdfService reportPdfService;

    @BeforeEach
    void setUp() {
        s3Uploader = mock(S3Uploader.class);
        reportPdfService = new ReportPdfService(new ReportPdfRenderer(new ReportHtmlRenderer()), s3Uploader);
    }

    private Report report(ReportStatus status) {
        Report report = Report.builder()
            .accident(mock(Accident.class))
            .narrative("교차로에서 충돌했다.")
            .summary(List.of("충돌"))
            .unverifiedItems(List.of())
            .disclaimer("AI가 작성한 초안입니다.")
            .build();
        ReflectionTestUtils.setField(report, "status", status);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 9, 21, 14, 30));
        return report;
    }

    @Test
    @DisplayName("확정된 경위서는 PDF로 저장되고 주소를 돌려준다")
    void storesConfirmedReport() {
        when(s3Uploader.upload(any(byte[].class), eq("pdf"), eq(FileCategory.REPORT_PDF)))
            .thenReturn(STORED_URL);

        String url = reportPdfService.store(report(ReportStatus.CONFIRMED));

        assertThat(url).isEqualTo(STORED_URL);
    }

    @Test
    @DisplayName("초안은 저장하지 않는다")
    void doesNotStoreDraft() {
        // 초안 PDF가 파일로 남으면 그게 보험사에 제출될 위험이 생긴다
        assertThatThrownBy(() -> reportPdfService.store(report(ReportStatus.DRAFT)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("확정된 경위서만");

        verify(s3Uploader, never()).upload(any(byte[].class), any(), any());
    }

    @Test
    @DisplayName("미리보기는 저장하지 않고 바이트만 돌려준다")
    void previewDoesNotStore() {
        byte[] pdf = reportPdfService.preview(report(ReportStatus.DRAFT));

        assertThat(pdf).isNotEmpty();
        verify(s3Uploader, never()).upload(any(byte[].class), any(), any());
    }

    @Test
    @DisplayName("S3 저장이 실패하면 예외가 그대로 올라간다")
    void propagatesStorageFailure() {
        S3CommunicationException failure = new S3CommunicationException("S3 업로드 실패: reports/x.pdf", null);
        when(s3Uploader.upload(any(byte[].class), eq("pdf"), eq(FileCategory.REPORT_PDF)))
            .thenThrow(failure);

        // 주소를 못 받으면 경위서에 담을 것이 없으므로 저장하는 쪽이 알아야 한다
        assertThatThrownBy(() -> reportPdfService.store(report(ReportStatus.CONFIRMED)))
            .isSameAs(failure);
    }

    @Test
    @DisplayName("저장된 주소를 경위서에 담을 수 있다")
    void attachesStoredUrlToReport() {
        Report report = report(ReportStatus.CONFIRMED);

        report.attachPdf(STORED_URL);

        assertThat(report.getPdfUrl()).isEqualTo(STORED_URL);
    }
}
