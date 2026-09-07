package com.sago.global.client.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.sago.domain.user.AuthProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 로그인 클라이언트.
 * 카카오는 이메일·닉네임이 모두 선택 동의 항목이라 응답에 없을 수 있다 — 없으면 null로 넘기고,
 * 회원 생성 시 AuthService가 대체값을 채운다.
 */
@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final OAuthProperties.Provider properties;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthProperties oAuthProperties) {
        this.properties = oAuthProperties.getKakao();
        this.restClient = buildRestClient();
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String authorizationCode) {
        String accessToken = requestAccessToken(authorizationCode);
        return requestUserInfo(accessToken);
    }

    private String requestAccessToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.getClientId());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("code", authorizationCode);
        if (properties.getClientSecret() != null && !properties.getClientSecret().isBlank()) {
            form.add("client_secret", properties.getClientSecret());
        }

        JsonNode response;
        try {
            response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new OAuthApiException("카카오 토큰 발급에 실패했습니다.", e);
        }

        if (response == null || !response.hasNonNull("access_token")) {
            throw new OAuthApiException("카카오 토큰 응답에 access_token이 없습니다.");
        }
        return response.get("access_token").asText();
    }

    private OAuthUserInfo requestUserInfo(String accessToken) {
        JsonNode response;
        try {
            response = restClient.get()
                .uri(USER_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new OAuthApiException("카카오 사용자 정보 조회에 실패했습니다.", e);
        }

        if (response == null || !response.hasNonNull("id")) {
            throw new OAuthApiException("카카오 사용자 정보 응답에 id가 없습니다.");
        }

        String providerUserId = response.get("id").asText();
        JsonNode account = response.path("kakao_account");
        String email = account.hasNonNull("email") ? account.get("email").asText() : null;
        JsonNode profile = account.path("profile");
        String nickname = profile.hasNonNull("nickname") ? profile.get("nickname").asText() : null;

        return new OAuthUserInfo(providerUserId, email, nickname);
    }

    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }
}
