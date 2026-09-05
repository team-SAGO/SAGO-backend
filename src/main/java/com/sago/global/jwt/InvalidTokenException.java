package com.sago.global.jwt;

/** 서명 불일치·만료·형식 오류 등 토큰을 신뢰할 수 없을 때 던진다. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
