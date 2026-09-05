package com.sago.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.global.exception.ErrorResponse;
import com.sago.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 시큐리티 설정. 세션을 쓰지 않고 요청마다 JWT로 인증한다.
 *
 * 인증이 필요 없는 경로(헬스체크·로그인·토큰 재발급)만 명시적으로 열어두고 나머지는 전부 인증을 요구한다.
 * 새 API를 추가할 때 별도 설정이 없으면 자동으로 보호되는 쪽이 안전하기 때문이다.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 헬스체크 및 문서
                .requestMatchers("/", "/health", "/api/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 로그인·토큰 재발급은 아직 토큰이 없는 상태에서 호출하므로 열어둔다
                .requestMatchers("/auth/social/*/callback").permitAll()
                .requestMatchers("/api/auth/social/*", "/api/auth/reissue").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(handler -> handler.authenticationEntryPoint(unauthorizedEntryPoint()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 인증 없이 보호된 경로에 접근했을 때의 응답.
     * 기본 동작은 로그인 페이지로 리다이렉트하는 것이라, API 서버에 맞게 401 JSON으로 바꾼다.
     */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse("UNAUTHORIZED", "로그인이 필요합니다.")
            );
        };
    }
}
