package com.sago.domain.user;

/** 토큰은 유효하지만 해당 회원이 없거나 이미 탈퇴한 경우에 던진다. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
