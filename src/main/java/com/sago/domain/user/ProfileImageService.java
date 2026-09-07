package com.sago.domain.user;

import com.sago.domain.user.ProfileImageStore.ImageReplacement;
import com.sago.domain.user.dto.ProfileResponse;
import com.sago.global.client.s3.FileCategory;
import com.sago.global.client.s3.S3Uploader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 업로드 (FR-01).
 *
 * S3 업로드는 롤백되지 않는 외부 호출이라 트랜잭션 밖에서 수행하고, DB 갱신만
 * {@link ProfileImageStore}의 짧은 트랜잭션에 맡긴다. 갱신이 실패하면 방금 올린 파일을 지운다.
 *
 * 이미지를 교체하면 직전 이미지는 아무도 참조하지 않으므로 S3에서 지운다.
 * 남겨두면 교체할 때마다 파일이 쌓여 저장 비용만 늘어난다.
 */
@Slf4j
@Service
public class ProfileImageService {

    private final S3Uploader s3Uploader;
    private final ProfileImageStore profileImageStore;

    public ProfileImageService(S3Uploader s3Uploader, ProfileImageStore profileImageStore) {
        this.s3Uploader = s3Uploader;
        this.profileImageStore = profileImageStore;
    }

    public ProfileResponse upload(Long userId, MultipartFile image) {
        profileImageStore.checkUploadable(userId);

        String newImageUrl = s3Uploader.upload(image, FileCategory.PROFILE_IMAGE);

        ImageReplacement replaced;
        try {
            replaced = profileImageStore.replaceImage(userId, newImageUrl);
        } catch (RuntimeException e) {
            deleteQuietly(newImageUrl);
            throw e;
        }

        // 교체가 확정된 뒤에 지운다. 먼저 지웠다가 갱신이 실패하면 이미지가 없는 프로필이 된다.
        if (replaced.previousImageUrl() != null) {
            deleteQuietly(replaced.previousImageUrl());
        }

        return replaced.profile();
    }

    /**
     * 정리용 삭제. 여기서 터진 예외로 원래 흐름이 막히면 안 되므로 삼키되,
     * 삼키기만 하면 고아 파일이 생긴 순간을 아무도 모르게 되므로 로그로 남긴다.
     */
    private void deleteQuietly(String imageUrl) {
        try {
            s3Uploader.delete(imageUrl);
        } catch (RuntimeException e) {
            log.warn("프로필 이미지 삭제 실패, S3에 고아 파일이 남습니다: {}", imageUrl, e);
        }
    }
}
