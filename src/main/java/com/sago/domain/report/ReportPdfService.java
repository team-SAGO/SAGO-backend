package com.sago.domain.report;

import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.S3Uploader;
import org.springframework.stereotype.Service;

/**
 * 확정된 경위서를 PDF로 만들어 S3에 저장한다.
 *
 * DB 저장은 하지 않는다. 저장된 주소를 돌려주기만 하고, 엔티티에 담아 커밋하는 것은
 * 경위서 저장을 맡은 쪽의 몫이다. S3 업로드는 롤백되지 않는 외부 호출이라
 * 트랜잭션 밖에 둬야 하기 때문이기도 하다.
 */
@Service
public class ReportPdfService {

    private static final String PDF_EXTENSION = "pdf";

    private final ReportPdfRenderer pdfRenderer;
    private final S3Uploader s3Uploader;

    public ReportPdfService(ReportPdfRenderer pdfRenderer, S3Uploader s3Uploader) {
        this.pdfRenderer = pdfRenderer;
        this.s3Uploader = s3Uploader;
    }

    /**
     * 확정된 경위서를 PDF로 저장하고 주소를 돌려준다.
     *
     * 초안은 저장하지 않는다. 사용자가 확인용으로 보는 것이라 파일로 남길 이유가 없고,
     * 남겨두면 그 초안이 보험사에 제출될 위험이 생긴다. 미리보기가 필요하면
     * {@link #preview}로 저장 없이 바이트만 받는다.
     */
    public String store(Report report) {
        if (report.getStatus() != ReportStatus.CONFIRMED) {
            throw new IllegalStateException(
                "확정된 경위서만 저장합니다. 현재 상태: " + report.getStatus());
        }

        byte[] pdf = pdfRenderer.render(report);
        return s3Uploader.upload(pdf, PDF_EXTENSION, FileCategory.REPORT_PDF);
    }

    /**
     * 저장하지 않고 PDF 바이트만 만든다. 초안 미리보기에 쓴다.
     * 초안이면 문서 안에 "초안 · 제출용 아님"이 찍힌다.
     */
    public byte[] preview(Report report) {
        return pdfRenderer.render(report);
    }
}
