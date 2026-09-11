package com.sago.domain.photo.dto;

import com.sago.domain.photo.Photo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사진 정보.
 *
 * 파일 주소(file_url)는 내보내지 않는다. 버킷이 비공개라 이 주소로는 열리지 않고, 그대로 내보내면
 * S3 오브젝트 키가 드러난다(음성 진술과 같은 이유). 화면에 띄울 주소는 presigned URL 발급이
 * 생기면 그때 붙인다.
 */
public record PhotoResponse(
    Long photoId,
    String category,
    BigDecimal latitude,
    BigDecimal longitude,
    LocalDateTime takenAt,
    LocalDateTime createdAt
) {

    public static PhotoResponse from(Photo photo) {
        return new PhotoResponse(
            photo.getPhotoId(),
            photo.getCategory(),
            photo.getLatitude(),
            photo.getLongitude(),
            photo.getTakenAt(),
            photo.getCreatedAt()
        );
    }
}
