package com.sago.domain.auth.dto;

/**
 * 발급된 서비스 토큰 한 쌍.
 *
 * @param accessToken           API 호출용. Authorization: Bearer 헤더에 담아 보낸다.
 * @param refreshToken          access 토큰 만료 시 재발급용.
 * @param accessTokenExpiresIn  access 토큰 만료까지 남은 시간(초).
 */
public record TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn) {
}
