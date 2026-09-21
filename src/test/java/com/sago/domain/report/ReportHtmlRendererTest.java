package com.sago.domain.report;

import com.sago.domain.accident.Accident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 경위서는 보험사에 제출될 수 있는 문서라, 무엇이 어떻게 적히는지가 곧 동작이다.
 */
class ReportHtmlRendererTest {

    private ReportHtmlRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new ReportHtmlRenderer();
    }

    private Report report(String narrative, List<String> summary, List<String> unverifiedItems,
                          ReportStatus status) {
        Report report = Report.builder()
            .accident(mock(Accident.class))
            .narrative(narrative)
            .summary(summary)
            .unverifiedItems(unverifiedItems)
            .disclaimer("본 경위서는 AI가 작성한 초안입니다.")
            .build();
        ReflectionTestUtils.setField(report, "status", status);
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.of(2026, 9, 21, 14, 30));
        return report;
    }

    private Report basicReport() {
        return report("교차로에서 직진 중 좌회전 차량과 충돌했다.",
            List.of("좌회전 차량과 충돌"), List.of(), ReportStatus.CONFIRMED);
    }

    @Test
    @DisplayName("미확인 항목은 본문과 분리된 섹션에 들어간다")
    void putsUnverifiedItemsInTheirOwnSection() {
        Report report = report("교차로에서 충돌했다.", List.of("충돌"),
            List.of("상대 차량 속도는 시속 60km로 추정됨"), ReportStatus.CONFIRMED);

        String html = renderer.render(report);

        int narrativeAt = html.indexOf("교차로에서 충돌했다.");
        int sectionAt = html.indexOf("확인이 필요한 내용");
        int itemAt = html.indexOf("상대 차량 속도는 시속 60km로 추정됨");

        // 추정 내용이 본문 안에 섞이면 사실로 읽힌다
        assertThat(narrativeAt).isLessThan(sectionAt);
        assertThat(sectionAt).isLessThan(itemAt);
    }

    @Test
    @DisplayName("미확인 항목에는 추정이라는 설명이 글로 붙는다")
    void explainsUnverifiedItemsInWords() {
        Report report = report("충돌했다.", List.of("충돌"), List.of("속도 추정"), ReportStatus.CONFIRMED);

        String html = renderer.render(report);

        // 색·기울임만으로 구분하면 흑백 인쇄에서 사라진다
        assertThat(html).contains("AI가 추정한 내용입니다");
        assertThat(html).contains("사실과 다를 수 있으므로");
    }

    @Test
    @DisplayName("미확인 항목이 없으면 그 섹션 자체가 없다")
    void omitsUnverifiedSectionWhenEmpty() {
        String html = renderer.render(basicReport());

        assertThat(html).doesNotContain("확인이 필요한 내용");
    }

    @Test
    @DisplayName("초안은 문서 안에 제출용이 아님을 적는다")
    void marksDraftInsideTheDocument() {
        Report draft = report("충돌했다.", List.of("충돌"), List.of(), ReportStatus.DRAFT);

        String html = renderer.render(draft);

        // 실수로 제출돼도 받는 쪽이 알아볼 수 있어야 한다
        assertThat(html).contains("초안 · 제출용 아님");
        assertThat(html).contains("<title>사고 경위서 (초안)</title>");
    }

    @Test
    @DisplayName("확정본에는 초안 표시가 없다")
    void doesNotMarkConfirmedReport() {
        String html = renderer.render(basicReport());

        // 고지 문구에도 "초안"이라는 말이 들어가므로, 표시 자체를 지목해서 확인한다
        assertThat(html).doesNotContain("초안 · 제출용 아님");
        assertThat(html).contains("<title>사고 경위서</title>");
    }

    @Test
    @DisplayName("진술에 섞인 꺾쇠와 앰퍼샌드를 이스케이프한다")
    void escapesMarkupFromGeneratedText() {
        Report report = report("상대가 <급정거> 했고 나 & 동승자가 다쳤다.",
            List.of("<급정거> 발생"), List.of("속도 <추정>"), ReportStatus.CONFIRMED);

        String html = renderer.render(report);

        // 이스케이프하지 않으면 올바른 XHTML이 아니게 되어 PDF 생성 자체가 실패한다
        assertThat(html).contains("&lt;급정거&gt;").contains("나 &amp; 동승자");
        assertThat(html).doesNotContain("<급정거>");
    }

    @Test
    @DisplayName("고지 문구는 항상 들어간다")
    void alwaysIncludesDisclaimer() {
        String html = renderer.render(basicReport());

        assertThat(html).contains("본 경위서는 AI가 작성한 초안입니다.");
    }

    @Test
    @DisplayName("본문의 줄바꿈은 문단으로 나뉜다")
    void splitsNarrativeIntoParagraphs() {
        Report report = report("첫째 줄이다.\n둘째 줄이다.", List.of("요약"), List.of(),
            ReportStatus.CONFIRMED);

        String html = renderer.render(report);

        assertThat(html).contains("<p>첫째 줄이다.</p>").contains("<p>둘째 줄이다.</p>");
    }

    @Test
    @DisplayName("작성 시각과 버전이 문서에 적힌다")
    void showsCreatedAtAndVersion() {
        String html = renderer.render(basicReport());

        assertThat(html).contains("2026년 9월 21일 14:30").contains("버전 1");
    }
}
