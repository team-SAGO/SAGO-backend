package com.sago.global.client.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private Provider kakao = new Provider();
    private Provider google = new Provider();

    public Provider getKakao() {
        return kakao;
    }

    public void setKakao(Provider kakao) {
        this.kakao = kakao;
    }

    public Provider getGoogle() {
        return google;
    }

    public void setGoogle(Provider google) {
        this.google = google;
    }

    public static class Provider {

        private String clientId;
        private String clientSecret;
        private String redirectUri;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }
}
