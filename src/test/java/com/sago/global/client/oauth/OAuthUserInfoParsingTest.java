package com.sago.global.client.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소셜 제공자 응답에서 이메일 검증 여부를 읽는 규칙 (#33).
 *
 * 이 값이 참이면 같은 이메일의 기존 회원에 연결되므로, 틀리게 참이 되면 계정 탈취로 이어진다.
 * 값이 없거나 애매한 응답은 모두 거짓이어야 한다.
 */
class OAuthUserInfoParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("카카오: 인증됐고 만료되지 않은 이메일만 검증된 것으로 본다")
    void kakaoRequiresVerifiedAndValid() throws Exception {
        assertThat(kakao(true, true).emailVerified()).isTrue();
        assertThat(kakao(true, false).emailVerified()).isFalse();
        // 다른 카카오계정에 쓰여 만료된 주소는 과거에 인증했더라도 지금의 소유자라고 볼 수 없다
        assertThat(kakao(false, true).emailVerified()).isFalse();
    }

    @Test
    @DisplayName("카카오: 검증 여부 값이 빠져 있으면 검증되지 않은 것으로 본다")
    void kakaoMissingFlagsAreNotVerified() throws Exception {
        OAuthUserInfo info = KakaoOAuthClient.toUserInfo(json("""
            {"id": 1, "kakao_account": {"email": "rider@example.com"}}"""));

        assertThat(info.email()).isEqualTo("rider@example.com");
        assertThat(info.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("카카오: 이메일 동의를 안 했으면 검증 값이 참이어도 검증되지 않은 것으로 본다")
    void kakaoWithoutEmailIsNotVerified() throws Exception {
        OAuthUserInfo info = KakaoOAuthClient.toUserInfo(json("""
            {"id": 1, "kakao_account": {"is_email_valid": true, "is_email_verified": true}}"""));

        assertThat(info.email()).isNull();
        assertThat(info.emailVerified()).isFalse();
    }

    @Test
    @DisplayName("구글: email_verified가 참일 때만 검증된 것으로 본다")
    void googleRequiresEmailVerified() throws Exception {
        assertThat(google("true").emailVerified()).isTrue();
        assertThat(google("false").emailVerified()).isFalse();
        assertThat(GoogleOAuthClient.toUserInfo(json("""
            {"sub": "g-1", "email": "rider@gmail.com"}""")).emailVerified()).isFalse();
    }

    private OAuthUserInfo kakao(boolean valid, boolean verified) throws Exception {
        return KakaoOAuthClient.toUserInfo(json("""
            {"id": 1, "kakao_account": {"email": "rider@example.com",
              "is_email_valid": %s, "is_email_verified": %s,
              "profile": {"nickname": "라이더"}}}""".formatted(valid, verified)));
    }

    private OAuthUserInfo google(String verified) throws Exception {
        return GoogleOAuthClient.toUserInfo(json("""
            {"sub": "g-1", "email": "rider@gmail.com", "email_verified": %s, "name": "라이더"}"""
            .formatted(verified)));
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
