package com.sago.domain.user;

import com.sago.domain.user.dto.ProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 업로드.
 *
 * {@link UserController}와 같은 /api/users/me 아래에 두되, 파일 업로드는 multipart라
 * JSON 기반의 프로필 조회·수정과 요청 형식이 달라 컨트롤러를 나눴다.
 */
@RestController
@RequestMapping("/api/users/me/profile-image")
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    public ProfileImageController(ProfileImageService profileImageService) {
        this.profileImageService = profileImageService;
    }

    @PostMapping
    public ProfileResponse uploadProfileImage(@AuthenticationPrincipal Long userId,
                                              @RequestPart("image") MultipartFile image) {
        return profileImageService.upload(userId, image);
    }
}
