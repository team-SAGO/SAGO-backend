package com.sago.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 기본 시큐리티 설정 (개발 초기 버전).
 * TODO: OAuth2(카카오/구글) 로그인, JWT 필터는 auth 기능 붙일 때 여기서 확장하세요.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 헬스체크 및 문서/인증 관련은 공개
                .requestMatchers("/", "/health", "/api/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                // 나머지는 인증 필요 (기능 붙이며 점진적으로 조정)
                .anyRequest().permitAll() // TODO: 실제 인증 붙으면 .authenticated() 로 변경
            );
        return http.build();
    }
}
