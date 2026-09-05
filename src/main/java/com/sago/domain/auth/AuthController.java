package com.sago.domain.auth;

import com.sago.domain.auth.dto.LoginResponse;
import com.sago.domain.auth.dto.ReissueRequest;
import com.sago.domain.auth.dto.TokenResponse;
import com.sago.domain.user.AuthProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 소셜 로그인/회원가입 엔드포인트 (FR-01).
 *
 * 두 가지 진입 경로를 지원한다.
 * - GET  /auth/social/{provider}/callback : 소셜 콘솔에 등록한 리다이렉트 URI로 직접 콜백이 오는 웹 흐름
 * - POST /api/auth/social/{provider}      : 앱이 인가 코드를 직접 받아 전달하는 모바일 흐름
 * 두 경로 모두 같은 서비스 로직을 타며 응답 형태도 같다.
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/auth/social/{provider}/callback")
    public LoginResponse socialLoginCallback(@PathVariable String provider,
                                             @RequestParam("code") String code) {
        return authService.login(parseProvider(provider), code);
    }

    @PostMapping("/api/auth/social/{provider}")
    public LoginResponse socialLogin(@PathVariable String provider,
                                     @Valid @RequestBody SocialLoginRequest request) {
        return authService.login(parseProvider(provider), request.code());
    }

    @PostMapping("/api/auth/reissue")
    public TokenResponse reissue(@Valid @RequestBody ReissueRequest request) {
        return authService.reissue(request.refreshToken());
    }

    private AuthProvider parseProvider(String provider) {
        try {
            return AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다: " + provider);
        }
    }

    public record SocialLoginRequest(
        @jakarta.validation.constraints.NotBlank(message = "code는 필수입니다.") String code) {
    }
}
