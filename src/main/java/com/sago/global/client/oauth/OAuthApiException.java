package com.sago.global.client.oauth;

/** 인가 코드 교환이나 사용자 정보 조회가 실패했을 때 던진다. */
public class OAuthApiException extends RuntimeException {

    public OAuthApiException(String message) {
        super(message);
    }

    public OAuthApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
