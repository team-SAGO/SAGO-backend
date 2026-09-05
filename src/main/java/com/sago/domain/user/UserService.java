package com.sago.domain.user;

import com.sago.domain.user.dto.ProfileResponse;
import com.sago.domain.user.dto.ProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 초기 설정과 회원정보 수정 (FR-01).
 *
 * 조회·수정 모두 인증된 본인만 대상으로 한다. userId를 요청 파라미터로 받지 않고
 * 토큰에서 꺼낸 값만 사용하므로, 남의 프로필을 지정해 접근할 방법이 없다.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return ProfileResponse.from(findActiveUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = findActiveUser(userId);
        user.updateProfile(request.nickname(), request.bikeModel(), request.bikeNumber());
        return ProfileResponse.from(user);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다."));
    }
}
