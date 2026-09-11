package com.sago.domain.photo.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사진 한 장에 붙는 정보. 전부 선택이다 — 위치 권한을 거부했거나 갤러리에서 고른 사진이면
 * 없을 수 있다.
 *
 * @param category  촬영 항목 (예: "번호판", "파손 부위"). AR 가이드가 안내한 항목이다.
 * @param latitude  촬영 위치 위도
 * @param longitude 촬영 위치 경도
 * @param takenAt   촬영 시각. 허용 범위를 넘은 미래 값은 저장하지 않는다.
 */
public record PhotoMetadata(

    @Size(max = 50, message = "촬영 항목은 50자 이하여야 합니다.")
    String category,

    @DecimalMin(value = "-90", message = "위도는 -90 이상 90 이하여야 합니다.")
    @DecimalMax(value = "90", message = "위도는 -90 이상 90 이하여야 합니다.")
    BigDecimal latitude,

    @DecimalMin(value = "-180", message = "경도는 -180 이상 180 이하여야 합니다.")
    @DecimalMax(value = "180", message = "경도는 -180 이상 180 이하여야 합니다.")
    BigDecimal longitude,

    LocalDateTime takenAt
) {

    /** 정보 없이 올린 사진. */
    public static final PhotoMetadata EMPTY = new PhotoMetadata(null, null, null, null);
}
