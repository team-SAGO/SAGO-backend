package com.sago.domain.user;

import com.sago.domain.user.dto.ProfileResponse;
import com.sago.domain.user.dto.ProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필 초기 설정 및 설정 화면의 회원정보 수정 (FR-01).
 *
 * 경로에 userId를 두지 않고 /me로 고정한다. 토큰의 주인만 자기 프로필을 다루게 되어
 * 다른 회원의 식별자를 넣어보는 접근 자체가 성립하지 않는다.
 *
 * principal에 담기는 userId는 JwtAuthenticationFilter가 넣어준 값이다.
 */
@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ProfileResponse getMyProfile(@AuthenticationPrincipal Long userId) {
        return userService.getProfile(userId);
    }

    @PatchMapping
    public ProfileResponse updateMyProfile(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(userId, request);
    }

    /**
     * 회원 탈퇴. 토큰의 주인만 자기 계정을 탈퇴시킬 수 있다.
     *
     * 계정은 사라지지만 사고 기록은 남는다 — 보험 처리의 근거 자료이기 때문이다.
     * 자세한 사정은 UserService.withdraw()에 적어두었다.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
    }
}
