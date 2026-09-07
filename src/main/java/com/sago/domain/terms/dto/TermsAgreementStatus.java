package com.sago.domain.terms.dto;

import com.sago.domain.terms.TermsType;

/**
 * 약관 하나에 대한 현재 동의 상태.
 *
 * @param agreed         현재 동의 여부. 동의한 적이 없으면 false다.
 * @param agreedVersion  동의한 약관 버전. 동의한 적이 없으면 null이다.
 * @param currentVersion terms.yml에 정의된 현재 버전.
 * @param reagreeNeeded  다시 동의를 받아야 하는지 여부. 클라이언트는 이 값만 보고
 *                       동의 화면을 다시 띄울지 판단하면 된다.
 */
public record TermsAgreementStatus(
    TermsType type,
    boolean required,
    String title,
    boolean agreed,
    String agreedVersion,
    String currentVersion,
    boolean reagreeNeeded
) {
}
