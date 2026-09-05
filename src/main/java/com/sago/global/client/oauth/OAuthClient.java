package com.sago.global.client.oauth;

import com.sago.domain.user.AuthProvider;

/**
 * 소셜 제공자별 인가 코드 → 사용자 정보 변환을 추상화한다.
 * 제공자를 추가할 때는 이 인터페이스 구현체만 늘리면 AuthService는 바뀌지 않는다.
 */
public interface OAuthClient {

    AuthProvider getProvider();

    /** 인가 코드로 액세스 토큰을 받고, 그 토큰으로 사용자 정보를 조회한다. */
    OAuthUserInfo fetchUserInfo(String authorizationCode);
}
