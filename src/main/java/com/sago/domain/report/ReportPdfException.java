package com.sago.domain.report;

/**
 * 경위서 PDF 생성 실패. 사용자가 손쓸 수 있는 문제가 아니므로
 * 원인은 로그로 남기고 응답에는 일반화된 안내를 보낸다.
 */
public class ReportPdfException extends RuntimeException {

    public ReportPdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
