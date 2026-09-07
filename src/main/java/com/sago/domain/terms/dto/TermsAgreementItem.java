package com.sago.domain.terms.dto;

import com.sago.domain.terms.TermsType;
import jakarta.validation.constraints.NotNull;

/**
 * 약관 하나에 대한 동의 여부.
 *
 * agreed를 Boolean으로 두고 필수로 검증한다 — boolean이면 값을 빠뜨렸을 때 false로 바인딩되어
 * "동의하지 않음"과 구분되지 않는다. 동의 기록은 근거로 쓰이는 값이라 그 둘을 섞으면 안 된다.
 */
public record TermsAgreementItem(

    @NotNull(message = "약관 유형은 필수입니다.")
    TermsType type,

    @NotNull(message = "동의 여부는 필수입니다.")
    Boolean agreed
) {
}
