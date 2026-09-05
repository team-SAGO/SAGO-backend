package com.sago.global.jwt;

/**
 * 토큰 종류. JWT의 "type" 클레임에 담아 발급하고 검증할 때 대조한다.
 * 이걸 두지 않으면 만료가 긴 refresh 토큰을 그대로 API 호출용 access 토큰처럼 쓸 수 있다.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
