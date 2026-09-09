package com.sago.global.client.s3;

/**
 * S3 통신이나 파일 읽기가 실패했을 때 발생 (네트워크·권한 오류, 잘못된 내부 상태 등).
 *
 * 사용자가 손쓸 수 있는 문제가 아니므로 원인은 로그로만 남기고, 응답에는 일반화된 안내를 보낸다.
 * 자격증명 오류 같은 내용이 그대로 나가면 내부 사정만 드러난다.
 */
public class S3CommunicationException extends S3UploadException {

    public S3CommunicationException(String message) {
        super(message);
    }

    public S3CommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
