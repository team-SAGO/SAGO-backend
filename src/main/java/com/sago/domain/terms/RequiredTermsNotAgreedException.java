package com.sago.domain.terms;

/** 필수 약관에 동의하지 않은 채로 동의 저장을 시도했을 때 던진다. */
public class RequiredTermsNotAgreedException extends RuntimeException {

    public RequiredTermsNotAgreedException(String message) {
        super(message);
    }
}
