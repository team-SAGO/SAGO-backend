package com.sago.domain.auth;

/** 지원하지 않는 소셜 제공자로 로그인을 시도했을 때 던진다. */
public class UnsupportedProviderException extends RuntimeException {

    public UnsupportedProviderException(String message) {
        super(message);
    }
}
