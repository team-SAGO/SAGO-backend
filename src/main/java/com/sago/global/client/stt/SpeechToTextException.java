package com.sago.global.client.stt;

/**
 * Google Cloud STT 호출 실패(인식 실패, 네트워크 오류 등) 시 발생.
 * 호출하는 서비스 레이어에서 이 예외를 잡아 재녹음·직접 입력 안내로 폴백한다.
 */
public class SpeechToTextException extends RuntimeException {

    public SpeechToTextException(String message) {
        super(message);
    }

    public SpeechToTextException(String message, Throwable cause) {
        super(message, cause);
    }
}
