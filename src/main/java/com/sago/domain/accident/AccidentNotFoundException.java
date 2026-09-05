package com.sago.domain.accident;

/** 존재하지 않는 사고이거나, 다른 회원의 사고에 접근한 경우에 던진다. */
public class AccidentNotFoundException extends RuntimeException {

    public AccidentNotFoundException(String message) {
        super(message);
    }
}
