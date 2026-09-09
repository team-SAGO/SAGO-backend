package com.sago.domain.user;

import com.sago.domain.user.ProfileImageStore.ImageReplacement;
import com.sago.domain.user.dto.ProfileResponse;
import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.S3CommunicationException;
import com.sago.global.client.s3.S3Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileImageServiceTest {

    private static final String NEW_URL = "https://sago.s3.ap-northeast-2.amazonaws.com/profiles/new.jpg";
    private static final String OLD_URL = "https://sago.s3.ap-northeast-2.amazonaws.com/profiles/old.jpg";

    private S3Uploader s3Uploader;
    private ProfileImageStore profileImageStore;
    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        s3Uploader = mock(S3Uploader.class);
        profileImageStore = mock(ProfileImageStore.class);
        profileImageService = new ProfileImageService(s3Uploader, profileImageStore);
    }

    private MultipartFile image() {
        return new MockMultipartFile("image", "profile.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    private ImageReplacement replacement(String previousUrl) {
        ProfileResponse profile =
            new ProfileResponse(1L, "rider@example.com", "라이더", null, "12가3456", NEW_URL, false);
        return new ImageReplacement(profile, previousUrl);
    }

    @Test
    @DisplayName("이미지를 교체하면 직전 이미지를 S3에서 지운다")
    void deletesPreviousImageAfterReplacement() {
        when(s3Uploader.upload(any(MultipartFile.class), eq(FileCategory.PROFILE_IMAGE)))
            .thenReturn(NEW_URL);
        when(profileImageStore.replaceImage(1L, NEW_URL)).thenReturn(replacement(OLD_URL));

        ProfileResponse response = profileImageService.upload(1L, image());

        assertThat(response.profileImageUrl()).isEqualTo(NEW_URL);
        verify(s3Uploader, times(1)).delete(OLD_URL);
    }

    @Test
    @DisplayName("처음 올리는 경우에는 삭제를 시도하지 않는다")
    void doesNotDeleteWhenNoPreviousImage() {
        when(s3Uploader.upload(any(MultipartFile.class), eq(FileCategory.PROFILE_IMAGE)))
            .thenReturn(NEW_URL);
        when(profileImageStore.replaceImage(1L, NEW_URL)).thenReturn(replacement(null));

        profileImageService.upload(1L, image());

        verify(s3Uploader, never()).delete(any());
    }

    @Test
    @DisplayName("DB 갱신이 실패하면 방금 올린 이미지를 지우고 예외를 전파한다")
    void rollsBackUploadedImageWhenUpdateFails() {
        when(s3Uploader.upload(any(MultipartFile.class), eq(FileCategory.PROFILE_IMAGE)))
            .thenReturn(NEW_URL);
        UserNotFoundException failure = new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다.");
        when(profileImageStore.replaceImage(1L, NEW_URL)).thenThrow(failure);

        assertThatThrownBy(() -> profileImageService.upload(1L, image()))
            .isSameAs(failure);

        verify(s3Uploader, times(1)).delete(NEW_URL);
    }

    @Test
    @DisplayName("직전 이미지 삭제가 실패해도 교체는 성공으로 끝난다")
    void succeedsEvenIfPreviousImageDeleteFails() {
        when(s3Uploader.upload(any(MultipartFile.class), eq(FileCategory.PROFILE_IMAGE)))
            .thenReturn(NEW_URL);
        when(profileImageStore.replaceImage(1L, NEW_URL)).thenReturn(replacement(OLD_URL));
        doThrow(new S3CommunicationException("삭제 실패")).when(s3Uploader).delete(OLD_URL);

        ProfileResponse response = profileImageService.upload(1L, image());

        // 이미 DB 갱신이 끝난 뒤라, 지난 파일을 못 지웠다고 사용자 요청을 실패시킬 이유는 없다
        assertThat(response.profileImageUrl()).isEqualTo(NEW_URL);
    }

    @Test
    @DisplayName("없는 회원이면 S3에 올리기 전에 실패한다")
    void checksUserBeforeUploading() {
        doThrow(new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다."))
            .when(profileImageStore).checkUploadable(1L);

        assertThatThrownBy(() -> profileImageService.upload(1L, image()))
            .isInstanceOf(UserNotFoundException.class);

        verify(s3Uploader, never()).upload(any(MultipartFile.class), any());
    }
}
