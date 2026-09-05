package com.sago.domain.user;

import com.sago.domain.user.dto.ProfileResponse;
import com.sago.domain.user.dto.ProfileUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("초기 설정 전에는 profileSet이 false다")
    void profileIsNotSetBeforeOnboarding() {
        givenUser(User.builder().email("rider@example.com").nickname("라이더").build());

        ProfileResponse response = userService.getProfile(1L);

        assertThat(response.profileSet()).isFalse();
        assertThat(response.bikeNumber()).isNull();
    }

    @Test
    @DisplayName("닉네임과 차량번호가 채워지면 profileSet이 true가 된다")
    void profileIsSetOnceRequiredFieldsFilled() {
        givenUser(User.builder().email("rider@example.com").build());

        ProfileResponse response = userService.updateProfile(
            1L, new ProfileUpdateRequest("라이더", "PCX125", "12가3456"));

        assertThat(response.profileSet()).isTrue();
        assertThat(response.nickname()).isEqualTo("라이더");
        assertThat(response.bikeModel()).isEqualTo("PCX125");
        assertThat(response.bikeNumber()).isEqualTo("12가3456");
    }

    @Test
    @DisplayName("보내지 않은 항목은 기존 값을 유지한다")
    void omittedFieldsAreKept() {
        User user = User.builder().email("rider@example.com").nickname("라이더").build();
        user.updateProfile(null, "PCX125", "12가3456");
        givenUser(user);

        ProfileResponse response = userService.updateProfile(
            1L, new ProfileUpdateRequest("새닉네임", null, null));

        assertThat(response.nickname()).isEqualTo("새닉네임");
        assertThat(response.bikeModel()).isEqualTo("PCX125");
        assertThat(response.bikeNumber()).isEqualTo("12가3456");
    }

    @Test
    @DisplayName("탈퇴한 회원의 프로필은 조회되지 않는다")
    void withdrawnUserProfileIsNotFound() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(1L))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("탈퇴한 회원의 프로필은 수정되지 않는다")
    void withdrawnUserProfileCannotBeUpdated() {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(
            1L, new ProfileUpdateRequest("라이더", null, null)))
            .isInstanceOf(UserNotFoundException.class);
    }

    private void givenUser(User user) {
        when(userRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
    }
}
