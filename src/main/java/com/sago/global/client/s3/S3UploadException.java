package com.sago.global.client.s3;

/**
 * S3 파일 처리 실패의 공통 상위 타입.
 *
 * 직접 던지지 않고 {@link S3ValidationException}과 {@link S3CommunicationException} 중
 * 하나를 쓴다. 두 실패는 사용자가 조치할 수 있는지가 다르고, 그에 따라 응답 코드와
 * 노출할 메시지가 갈리기 때문이다.
 *
 * 다만 업로드 후 되돌리기처럼 <b>실패 원인과 무관하게 정리만 하는 경로</b>는
 * 이 타입으로 함께 잡는다.
 */
public abstract class S3UploadException extends RuntimeException {

    protected S3UploadException(String message) {
        super(message);
    }

    protected S3UploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
