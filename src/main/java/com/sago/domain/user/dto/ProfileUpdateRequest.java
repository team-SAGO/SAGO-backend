package com.sago.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 프로필 수정 요청. 보내지 않은(null) 항목은 기존 값을 그대로 둔다.
 *
 * 빈 문자열은 null과 구분해서 거부한다 — 실수로 빈 값이 넘어와 닉네임이 사라지는 것을 막기 위함이다.
 */
public record ProfileUpdateRequest(

    @Size(min = 1, max = 100, message = "닉네임은 1자 이상 100자 이하여야 합니다.")
    String nickname,

    @Size(min = 1, max = 100, message = "차종은 100자 이하여야 합니다.")
    String bikeModel,

    // 이륜차 번호판은 "12가3456", "서울12가3456"처럼 지역명이 붙는 경우가 있어 넉넉하게 허용한다.
    @Size(min = 2, max = 20, message = "차량번호는 20자 이하여야 합니다.")
    @Pattern(regexp = "^[0-9가-힣]+$", message = "차량번호는 숫자와 한글만 입력할 수 있습니다.")
    String bikeNumber
) {
}
