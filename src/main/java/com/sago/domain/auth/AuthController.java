package com.sago.domain.auth;

import com.sago.domain.auth.dto.LoginResponse;
import com.sago.domain.auth.dto.LogoutRequest;
import com.sago.domain.auth.dto.ReissueRequest;
import com.sago.domain.auth.dto.TokenResponse;
import com.sago.domain.user.AuthProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 소셜 로그인/회원가입 엔드포인트 (FR-01).
 *
 * 진입 경로가 둘이고, 경로 접두사가 서로 다르다. 오타가 아니라 다음 이유 때문이다.
 *
 * - GET /auth/social/{provider}/callback
 *     카카오·구글 개발자 콘솔에 등록하는 리다이렉트 URI다. 콘솔 등록값과 .env의
 *     KAKAO_REDIRECT_URI / GOOGLE_REDIRECT_URI 가 글자 그대로 일치해야 하므로,
 *     다른 API처럼 /api 접두사를 붙일 수 없다. 소셜 서버가 브라우저를 이 주소로 되돌려보낸다.
 *
 * - POST /api/auth/social/{provider}
 *     앱이 인가 코드를 직접 받아 body로 전달하는 모바일 흐름이다. 우리 API이므로 /api를 쓴다.
 *
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

    /**
     * 로그아웃. 넘겨받은 refresh 토큰을 무효화한다.
     *
     * 인증을 요구하지 않는다. 로그아웃하려는 시점에 access 토큰이 이미 만료됐을 수 있는데,
     * 그때 인증으로 막으면 사용자는 토큰을 무효화할 방법이 없어진다. 자기가 가진 토큰을
     * 버리는 요청이라 인증을 요구할 실익도 없다.
     *
     * 이미 무효화된 토큰이어도 성공으로 응답한다 — 로그아웃을 두 번 눌렀다고 오류를 낼 이유가 없다.
     */
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    private AuthProvider parseProvider(String provider) {
        return Arrays.stream(AuthProvider.values())
            .filter(candidate -> candidate.name().equalsIgnoreCase(provider))
            .findFirst()
            .orElseThrow(() -> new UnsupportedProviderException(
                "지원하지 않는 소셜 로그인입니다: " + provider));
    }

    public record SocialLoginRequest(@NotBlank(message = "code는 필수입니다.") String code) {
    }
}
