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

    @Size(min = 1, max = 100, message = "차종은 1자 이상 100자 이하여야 합니다.")
    String bikeModel,

    /*
     * 이륜차 번호판은 "12가3456"처럼 지역명이 없는 것부터 "서울강남 가1234"처럼 지역명과
     * 공백이 들어가는 표기까지 형태가 다양하다. 경위서·보험 서류에 그대로 실릴 값이라
     * 형식을 좁게 잡아 반려하는 쪽이 더 위험하다고 보고, 공백을 허용하되 숫자·한글 외의
     * 문자만 거른다. 저장 전에 앞뒤 공백과 중복 공백은 UserService에서 정리한다.
     */
    @Size(min = 2, max = 20, message = "차량번호는 2자 이상 20자 이하여야 합니다.")
    @Pattern(
        regexp = "^[0-9가-힣]+( ?[0-9가-힣]+)*$",
        message = "차량번호는 숫자와 한글만 입력할 수 있습니다.")
    String bikeNumber
) {
}
