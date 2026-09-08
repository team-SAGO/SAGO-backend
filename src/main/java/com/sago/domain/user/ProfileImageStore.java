package com.sago.domain.user;

import com.sago.domain.user.dto.ProfileResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 이미지 교체의 DB 작업만 담당한다.
 *
 * {@link ProfileImageService}에서 분리한 이유는 S3 업로드를 트랜잭션 밖에 두기 위해서다.
 * 업로드는 롤백되지 않는 외부 호출이라, 트랜잭션 안에서 수행하면 파일이 다 올라갈 때까지
 * DB 커넥션을 붙잡게 된다. 같은 빈 안에서 나눠두면 프록시가 적용되지 않아 별도 빈으로 뺐다.
 */
@Component
public class ProfileImageStore {

    private final UserRepository userRepository;

    public ProfileImageStore(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 업로드를 시작하기 전에 회원이 존재하는지 확인한다.
     * 없는 회원 때문에 S3에 올렸다가 도로 지우는 낭비를 막기 위한 사전 검사다.
     */
    @Transactional(readOnly = true)
    public void checkUploadable(Long userId) {
        findActiveUser(userId);
    }

    /**
     * 프로필 이미지를 새 주소로 바꾸고, 갱신된 프로필과 직전 이미지 주소를 함께 돌려준다.
     * 직전 주소를 함께 넘기는 것은 호출자가 트랜잭션 밖에서 이전 파일을 지우기 위해서다.
     */
    @Transactional
    public ImageReplacement replaceImage(Long userId, String newImageUrl) {
        User user = findActiveUser(userId);

        String previousImageUrl = user.getProfileImageUrl();
        user.updateProfileImage(newImageUrl);

        return new ImageReplacement(ProfileResponse.from(user), previousImageUrl);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다."));
    }

    /**
     * @param profile          교체 후 프로필
     * @param previousImageUrl 교체 전 이미지 주소. 처음 올리는 경우 null이다.
     */
    public record ImageReplacement(ProfileResponse profile, String previousImageUrl) {
    }
}
