package com.sago.domain.report;

import com.sago.domain.accident.Accident;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PDF 기본 폰트에는 한글 글리프가 없다. 폰트를 물리지 못하면 본문이 빈칸이나 네모로 나오는데,
 * 바이트가 만들어졌는지만 보면 그 실패가 드러나지 않는다.
 * 그래서 만든 PDF에서 글자를 다시 뽑아 한글이 살아 있는지 확인한다.
 */
class ReportPdfRendererTest {

    private ReportPdfRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ReportPdfRenderer(new ReportHtmlRenderer());
    }

    private Report report(ReportStatus status) {
        Report report = Report.builder()
            .accident(mock(Accident.class))
            .narrative("교차로에서 직진 중 좌회전 차량과 충돌했다. 상대 차량 운전자와 연락처를 교환했다.")
            .summary(List.of("좌회전 차량과 충돌", "연락처 교환 완료"))
            .unverifiedItems(List.of("상대 차량 속도는 추정값이다"))
            .disclaimer("본 경위서는 AI가 작성한 초안이며 법적 효력이 없습니다.")
            .build();
        ReflectionTestUtils.setField(report, "status", status);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 9, 21, 14, 30));
        return report;
    }

    private String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return squeeze(new PDFTextStripper().getText(document));
        }
    }

    /**
     * 공백을 모두 걷어낸다. PDF에서 글자를 뽑으면 줄바꿈 위치와 공백 문자가 원문과 달라져서,
     * 그대로 비교하면 글자가 멀쩡히 들어 있어도 어긋난다. 여기서 확인하려는 것은
     * 한글 글자가 살아 있는지이지 공백이 같은지가 아니다.
     */
    private String squeeze(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if (!Character.isWhitespace(character) && character != ' ') {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    @Test
    @DisplayName("만들어진 PDF에서 한글 본문을 다시 읽을 수 있다")
    void keepsKoreanTextInGeneratedPdf() throws IOException {
        byte[] pdf = renderer.render(report(ReportStatus.CONFIRMED));

        String text = textOf(pdf);

        assertThat(text).contains(squeeze("교차로에서 직진 중 좌회전 차량과 충돌했다"));
        assertThat(text).contains(squeeze("좌회전 차량과 충돌"));
    }

    @Test
    @DisplayName("미확인 항목과 그 설명이 PDF에 남는다")
    void keepsUnverifiedSectionInPdf() throws IOException {
        byte[] pdf = renderer.render(report(ReportStatus.CONFIRMED));

        String text = textOf(pdf);

        // 추정이라는 표시가 PDF까지 살아남아야 의미가 있다
        assertThat(text).contains(squeeze("확인이 필요한 내용"));
        assertThat(text).contains(squeeze("AI가 추정한 내용입니다"));
        assertThat(text).contains(squeeze("상대 차량 속도는 추정값이다"));
    }

    @Test
    @DisplayName("초안 표시가 PDF 안에 찍힌다")
    void marksDraftInPdf() throws IOException {
        byte[] pdf = renderer.render(report(ReportStatus.DRAFT));

        assertThat(textOf(pdf)).contains(squeeze("초안 · 제출용 아님"));
    }

    @Test
    @DisplayName("한글 폰트가 문서에 임베딩된다")
    void embedsKoreanFont() throws IOException {
        byte[] pdf = renderer.render(report(ReportStatus.CONFIRMED));

        try (PDDocument document = PDDocument.load(pdf)) {
            PDPage page = document.getPage(0);
            List<String> fontNames = new java.util.ArrayList<>();
            for (org.apache.pdfbox.cos.COSName name : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(name);
                fontNames.add(font.getName());
            }

            // 임베딩에 실패하면 기본 폰트로 대체되어 한글이 빠진다
            assertThat(fontNames).anyMatch(name -> name.contains("Pretendard"));
        }
    }

    @Test
    @DisplayName("PDF 형식으로 만들어진다")
    void producesPdfBytes() {
        byte[] pdf = renderer.render(report(ReportStatus.CONFIRMED));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1))
            .isEqualTo("%PDF-");
    }
}
