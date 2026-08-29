package com.sago.global.client.gemini;

/**
 * Gemini API 호출 실패(비정상 응답, 무료 한도 초과 등) 시 발생.
 * 각 기능(체크리스트·보완질문 등) 서비스 레이어에서 이 예외를 잡아 정적 폴백을 적용한다.
 */
public class GeminiApiException extends RuntimeException {

    public GeminiApiException(String message) {
        super(message);
    }

    public GeminiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
