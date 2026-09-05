package com.sago.global.client.oauth;

/**
 * 소셜 제공자에서 받아온 사용자 정보를 provider에 상관없이 동일한 형태로 다루기 위한 값 객체.
 *
 * @param providerUserId 제공자가 부여한 고유 식별자 (카카오 id, 구글 sub)
 * @param email          이메일. 카카오는 사용자가 동의하지 않으면 내려주지 않으므로 null일 수 있다.
 * @param nickname       닉네임. 없을 수 있다.
 */
public record OAuthUserInfo(String providerUserId, String email, String nickname) {
}
