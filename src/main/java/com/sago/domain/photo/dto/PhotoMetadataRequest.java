package com.sago.domain.photo.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * 업로드 요청의 {@code metadata} 파트. 파일과 같은 순서로 장별 정보를 담는다.
 *
 * 배열을 바로 받지 않고 한 번 감싼 이유는 검증 때문이다. 리스트를 파트로 바로 받으면 원소 검증이
 * 다른 요청과 같은 경로(MethodArgumentNotValidException → 400 INVALID_REQUEST)를 타지 않는다.
 * 객체의 필드로 두면 {@code @Valid}가 원소까지 내려가고 같은 형식의 400으로 나간다.
 *
 * @param photos 장별 정보. 파일 개수와 같아야 한다.
 */
public record PhotoMetadataRequest(

    @Valid
    List<PhotoMetadata> photos
) {
}
