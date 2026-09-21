package com.sago.domain.report;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.PageSizeUnits;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 경위서 HTML을 PDF 바이트로 변환한다.
 *
 * <b>한글 폰트를 직접 물려야 한다.</b> PDF 표준 기본 폰트(Helvetica·Times·Courier)에는
 * 한글 글리프가 없어서, 임베딩하지 않으면 본문이 통째로 빈칸이나 네모로 나온다.
 * 영문만으로 시험하면 멀쩡해 보이다가 실제 경위서에서 드러나는 종류의 문제다.
 *
 * 가변 폰트(PretendardVariable.ttf)는 쓰지 않는다. PDFBox가 굵기 축을 해석하지 못해
 * 굵기가 무시되거나 렌더링이 깨진다. 굵기별 정적 TTF를 따로 등록한다.
 */
@Component
public class ReportPdfRenderer {

    private static final String FONT_FAMILY = "Pretendard";
    private static final String REGULAR_FONT = "fonts/Pretendard-Regular.ttf";
    private static final String BOLD_FONT = "fonts/Pretendard-Bold.ttf";

    private static final float A4_WIDTH_MM = 210f;
    private static final float A4_HEIGHT_MM = 297f;

    private final ReportHtmlRenderer htmlRenderer;

    public ReportPdfRenderer(ReportHtmlRenderer htmlRenderer) {
        this.htmlRenderer = htmlRenderer;
        // 폰트가 없거나 CSS를 못 읽어도 경고만 남기고 넘어가는 라이브러리라,
        // 기동 시 폰트 존재를 먼저 확인한다. 없으면 글자가 빠진 PDF가 조용히 만들어진다.
        requireFont(REGULAR_FONT);
        requireFont(BOLD_FONT);
        XRLog.setLoggingEnabled(false);
    }

    public byte[] render(Report report) {
        String html = htmlRenderer.render(report);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.useDefaultPageSize(A4_WIDTH_MM, A4_HEIGHT_MM, PageSizeUnits.MM);
            builder.useFont(() -> openFont(REGULAR_FONT), FONT_FAMILY, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> openFont(BOLD_FONT), FONT_FAMILY, 700, FontStyle.NORMAL, true);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReportPdfException("경위서 PDF를 만들지 못했습니다", e);
        }
    }

    private InputStream openFont(String path) {
        try {
            return new ClassPathResource(path).getInputStream();
        } catch (IOException e) {
            throw new ReportPdfException("경위서 폰트를 읽지 못했습니다: " + path, e);
        }
    }

    private void requireFont(String path) {
        if (!new ClassPathResource(path).exists()) {
            throw new IllegalStateException(
                "경위서 폰트가 없습니다: " + path + ". 없으면 한글이 빠진 PDF가 만들어집니다.");
        }
    }
}
