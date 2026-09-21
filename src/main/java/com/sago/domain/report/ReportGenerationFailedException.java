package com.sago.domain.report;

/**
 * AI가 경위서를 만들지 못한 경우. Gemini 호출 실패와 응답 형식 오류를 모두 포함한다.
 *
 * 사용자가 할 수 있는 일이 "다시 시도" 또는 "직접 작성"이라 그 둘을 안내하는 것이 이 예외의 목적이다.
 */
public class ReportGenerationFailedException extends RuntimeException {

    public ReportGenerationFailedException(String message) {
        super(message);
    }
}
