package com.sago.global.client.s3;

/**
 * 업로드하려는 파일이 조건을 만족하지 못할 때 발생 (확장자·용량·개수·빈 파일).
 *
 * 사용자가 다른 파일을 고르면 해결되는 문제이므로, 메시지를 그대로 보여줘도 된다.
 * "10MB 이하로 줄여주세요" 같은 안내가 실제 행동으로 이어진다.
 */
public class S3ValidationException extends S3UploadException {

    public S3ValidationException(String message) {
        super(message);
    }

    public S3ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
