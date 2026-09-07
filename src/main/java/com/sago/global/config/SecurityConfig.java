package com.sago.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sago.global.exception.ErrorResponse;
import com.sago.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 시큐리티 설정. 세션을 쓰지 않고 요청마다 JWT로 인증한다.
 *
 * 인증이 필요 없는 경로(헬스체크·로그인·토큰 재발급)만 명시적으로 열어두고 나머지는 전부 인증을 요구한다.
 * 새 API를 추가할 때 별도 설정이 없으면 자동으로 보호되는 쪽이 안전하기 때문이다.
 */
@Configuration
public class SecurityConfig {

    private static final long CORS_PREFLIGHT_MAX_AGE_SECONDS = 3600;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsProperties corsProperties,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsProperties = corsProperties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
                // 약관 목록은 가입 전에도 확인할 수 있어야 한다 (동의 저장은 인증 필요)
                .requestMatchers(HttpMethod.GET, "/api/terms").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(handler -> handler.authenticationEntryPoint(unauthorizedEntryPoint()))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 프론트엔드가 별도 오리진에서 뜨므로 CORS 허용이 필요하다.
     * csrf.disable()과는 별개 문제로, 이게 없으면 브라우저가 preflight 단계에서 요청을 막는다.
     *
     * 오리진은 와일드카드가 아니라 목록으로 명시한다. 자격 증명을 함께 보내지는 않지만,
     * 아무 사이트에서나 이 API를 브라우저로 호출할 수 있게 열어둘 이유가 없다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        configuration.setMaxAge(CORS_PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
