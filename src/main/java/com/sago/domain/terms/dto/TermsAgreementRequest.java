package com.sago.domain.terms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TermsAgreementRequest(

    @NotEmpty(message = "동의 내역은 비어 있을 수 없습니다.")
    @Valid
    List<TermsAgreementItem> agreements
) {
}
