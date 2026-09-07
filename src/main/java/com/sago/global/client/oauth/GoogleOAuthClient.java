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
 * 구글 로그인 클라이언트.
 * 사용자 식별자로는 이메일이 아니라 변하지 않는 sub 값을 쓴다 — 이메일은 사용자가 바꿀 수 있어
 * 계정 식별자로 삼으면 같은 사람이 다른 회원으로 잡힌다.
 */
@Component
public class GoogleOAuthClient implements OAuthClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final OAuthProperties.Provider properties;
    private final RestClient restClient;

    public GoogleOAuthClient(OAuthProperties oAuthProperties) {
        this.properties = oAuthProperties.getGoogle();
        this.restClient = buildRestClient();
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
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
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("code", authorizationCode);

        JsonNode response;
        try {
            response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new OAuthApiException("구글 토큰 발급에 실패했습니다.", e);
        }

        if (response == null || !response.hasNonNull("access_token")) {
            throw new OAuthApiException("구글 토큰 응답에 access_token이 없습니다.");
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
            throw new OAuthApiException("구글 사용자 정보 조회에 실패했습니다.", e);
        }

        if (response == null || !response.hasNonNull("sub")) {
            throw new OAuthApiException("구글 사용자 정보 응답에 sub가 없습니다.");
        }

        String providerUserId = response.get("sub").asText();
        String email = response.hasNonNull("email") ? response.get("email").asText() : null;
        String nickname = response.hasNonNull("name") ? response.get("name").asText() : null;

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
