package com.sago.domain.auth.dto;

/**
 * 소셜 로그인 결과.
 *
 * newUser가 true면 이번 요청에서 회원이 새로 만들어졌다는 뜻이다.
 * 클라이언트는 이 값으로 약관 동의·역할 선택·프로필 초기 설정 온보딩으로 보낼지,
 * 바로 홈으로 보낼지 결정한다.
 *
 * accountLinked가 true면 처음 쓰는 소셜 계정이지만 같은 이메일의 기존 회원에 연결됐다는 뜻이다 (#33).
 * 이때 newUser는 false다. 사용자가 모르는 사이에 계정이 합쳐진 것처럼 느끼지 않도록
 * "기존 계정에 연결되었습니다"처럼 알려주는 데 쓴다.
 */
public record LoginResponse(TokenResponse token, boolean newUser, boolean accountLinked) {
}
