package com.sago.domain.photo.dto;

import com.sago.global.client.s3.MultiUploadResult.FailedUpload;

import java.util.List;

/**
 * 사진 업로드 결과. 일부가 실패해도 성공한 사진은 저장된다(#39).
 *
 * 실패가 있어도 200으로 나간다. 클라이언트는 상태 코드가 아니라 {@code failures}가 비어 있는지로
 * 판단해 실패한 사진을 안내해야 한다.
 *
 * @param photos   저장된 사진. 요청 순서를 유지한다.
 * @param failures 올리지 못한 사진. 요청에서의 순번과 파일명, 사용자가 할 행동(파일 교체/재시도)을 담는다.
 */
public record PhotoUploadResponse(List<PhotoResponse> photos, List<FailedUpload> failures) {
}
