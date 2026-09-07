package com.sago.domain.auth;

/** 탈퇴 처리된 회원이 다시 로그인을 시도했을 때 던진다. */
public class WithdrawnUserException extends RuntimeException {

    public WithdrawnUserException(String message) {
        super(message);
    }
}
