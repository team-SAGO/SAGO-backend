package com.sago.domain.report;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 경위서를 PDF로 찍기 전 단계인 XHTML 문서로 만든다.
 *
 * OpenHTMLtoPDF가 HTML을 받아 그대로 그리는 방식이라, <b>무엇을 어떻게 보여줄지에 대한 판단은
 * 전부 여기에 있다.</b> 변환 단계는 폰트를 물리고 바이트를 뽑는 일만 한다.
 *
 * 문서 문자열은 Gemini 응답에서 오고 사용자 진술이 그대로 반영되므로 전부 이스케이프한다.
 * 이스케이프하지 않으면 내용이 깨져 보이는 정도가 아니라, 올바른 XHTML이 아니게 되어
 * PDF 생성 자체가 실패한다.
 */
@Component
public class ReportHtmlRenderer {

    private static final DateTimeFormatter CREATED_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm");

    public String render(Report report) {
        StringBuilder html = new StringBuilder(2048);

        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"ko\"><head><meta charset=\"UTF-8\" />")
            .append("<title>").append(escape(documentTitle(report))).append("</title>")
            .append("<style>").append(styles()).append("</style>")
            .append("</head><body>");

        appendHeader(html, report);
        appendNarrative(html, report);
        appendSummary(html, report);
        appendUnverifiedItems(html, report);
        appendDisclaimer(html, report);

        html.append("</body></html>");
        return html.toString();
    }

    private String documentTitle(Report report) {
        return report.getStatus() == ReportStatus.DRAFT ? "사고 경위서 (초안)" : "사고 경위서";
    }

    private void appendHeader(StringBuilder html, Report report) {
        html.append("<h1>사고 경위서</h1>");

        // 초안이 실수로 제출돼도 받는 쪽이 바로 알아볼 수 있도록 문서 안에 상태를 적는다.
        // 확정본과 겉모습이 같으면 어느 쪽인지 구분할 방법이 없다.
        if (report.getStatus() == ReportStatus.DRAFT) {
            html.append("<p class=\"draft-mark\">초안 · 제출용 아님</p>");
        }

        html.append("<p class=\"meta\">작성 ")
            .append(escape(report.getCreatedAt().format(CREATED_AT_FORMAT)))
            .append(" · 버전 ").append(report.getVersion())
            .append("</p>");
    }

    private void appendNarrative(StringBuilder html, Report report) {
        html.append("<h2>사고 경위</h2>");
        appendParagraphs(html, report.getNarrative());
    }

    private void appendSummary(StringBuilder html, Report report) {
        List<String> summary = report.getSummary();
        if (summary == null || summary.isEmpty()) {
            return;
        }
        html.append("<h2>요약</h2><ul>");
        summary.forEach(line -> html.append("<li>").append(escape(line)).append("</li>"));
        html.append("</ul>");
    }

    /**
     * 미확인 항목은 본문과 분리된 섹션으로 둔다.
     *
     * AI가 추론했지만 확인되지 않은 내용이라, 본문에 섞이면 추정이 사실로 읽힌다.
     * 색이나 기울임으로만 구분하면 인쇄하거나 흑백으로 출력할 때 사라지므로,
     * 글로 된 머리말을 함께 넣어 출력 환경과 무관하게 남게 한다.
     */
    private void appendUnverifiedItems(StringBuilder html, Report report) {
        List<String> unverifiedItems = report.getUnverifiedItems();
        if (unverifiedItems == null || unverifiedItems.isEmpty()) {
            return;
        }
        html.append("<h2>확인이 필요한 내용</h2>")
            .append("<p class=\"unverified-notice\">")
            .append("아래 항목은 진술에서 확인되지 않아 AI가 추정한 내용입니다. ")
            .append("사실과 다를 수 있으므로 확인 후 사용하세요.")
            .append("</p><ul class=\"unverified\">");
        unverifiedItems.forEach(item -> html.append("<li>").append(escape(item)).append("</li>"));
        html.append("</ul>");
    }

    private void appendDisclaimer(StringBuilder html, Report report) {
        html.append("<p class=\"disclaimer\">").append(escape(report.getDisclaimer())).append("</p>");
    }

    /**
     * 본문의 줄바꿈을 문단으로 바꾼다.
     * 그대로 두면 PDF에서 전부 한 덩어리로 붙어 읽기 어려워진다.
     */
    private void appendParagraphs(StringBuilder html, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String paragraph : text.lines().toList()) {
            if (!paragraph.isBlank()) {
                html.append("<p>").append(escape(paragraph.strip())).append("</p>");
            }
        }
    }

    /**
     * XHTML에서 의미를 갖는 문자를 실체 참조로 바꾼다.
     * 따옴표까지 바꾸는 것은 나중에 속성값에 넣게 되더라도 안전하도록 하기 위함이다.
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String styles() {
        return """
            body { font-family: 'Pretendard', sans-serif; font-size: 11pt; line-height: 1.6; }
            h1 { font-size: 18pt; text-align: center; margin-bottom: 4pt; }
            h2 { font-size: 13pt; margin-top: 18pt; border-bottom: 1px solid #000; padding-bottom: 2pt; }
            .draft-mark { text-align: center; font-weight: bold; border: 2px solid #000; padding: 4pt; }
            .meta { text-align: center; font-size: 9pt; margin-bottom: 16pt; }
            .unverified-notice { font-weight: bold; }
            .unverified li { margin-bottom: 4pt; }
            .disclaimer { margin-top: 24pt; font-size: 9pt; border-top: 1px solid #000; padding-top: 6pt; }
            """;
    }
}
