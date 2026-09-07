package com.sago.domain.user;

import com.sago.domain.auth.RefreshTokenStore;
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
    private final RefreshTokenStore refreshTokenStore;

    public UserService(UserRepository userRepository, RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return ProfileResponse.from(findActiveUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = findActiveUser(userId);
        user.updateProfile(
            request.nickname(),
            request.bikeModel(),
            normalizeBikeNumber(request.bikeNumber()));
        return ProfileResponse.from(user);
    }

    /**
     * 회원 탈퇴.
     *
     * 행을 지우지 않고 deletedAt만 채운다. 사고 기록은 보험 처리의 근거 자료라 회원이 나갔다고
     * 함께 사라지면 안 되고, accident가 user를 참조하고 있어 물리 삭제 자체가 불가능하다.
     *
     * 남아 있는 refresh 토큰은 모두 무효화한다. 그러지 않으면 탈퇴한 뒤에도 다른 기기에서
     * 재발급으로 계속 접근할 수 있다.
     *
     * TODO: 탈퇴 후에도 이메일·닉네임이 그대로 남는다. 개인정보를 어디까지 지울지는
     *       법률 검토가 필요해 별도 이슈로 다룬다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = findActiveUser(userId);
        user.withdraw();
        refreshTokenStore.revokeAll(userId);
    }

    /**
     * 차량번호의 공백 표기를 하나로 맞춘다.
     *
     * 번호판을 "서울강남 가1234"처럼 띄어 쓰는 사람과 붙여 쓰는 사람이 섞여 있어,
     * 입력을 넓게 받되 저장 형태는 통일한다. 그러지 않으면 같은 번호판이 여러 표기로 남아
     * 나중에 번호로 조회하거나 대조할 때 어긋난다.
     */
    private String normalizeBikeNumber(String bikeNumber) {
        if (bikeNumber == null) {
            return null;
        }
        return bikeNumber.trim().replaceAll("\\s+", " ");
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다."));
    }
}
