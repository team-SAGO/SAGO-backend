package com.sago.global.client.s3;

/**
 * S3 업로드·삭제 실패(검증 실패, 네트워크 오류, 권한 오류 등) 시 발생.
 * 호출하는 서비스 레이어에서 이 예외를 잡아 재시도·사용자 안내로 폴백한다.
 */
public class S3UploadException extends RuntimeException {

    public S3UploadException(String message) {
        super(message);
    }

    public S3UploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
