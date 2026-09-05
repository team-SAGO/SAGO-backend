package com.sago.domain.auth.dto;

/**
 * 소셜 로그인 결과.
 *
 * newUser가 true면 이번 요청에서 회원이 새로 만들어졌다는 뜻이다.
 * 클라이언트는 이 값으로 약관 동의·역할 선택·프로필 초기 설정 온보딩으로 보낼지,
 * 바로 홈으로 보낼지 결정한다.
 */
public record LoginResponse(TokenResponse token, boolean newUser) {
}
