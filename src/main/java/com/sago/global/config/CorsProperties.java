package com.sago.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 허용할 프론트엔드 오리진. 로컬 개발 서버와 배포 도메인이 다르므로 코드가 아니라
 * application.yml / 환경변수로 관리한다.
 */
@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
