package com.sago.domain.checklist.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 체크리스트 항목의 완료 여부 변경.
 *
 * Boolean으로 받아 필수로 검증한다 — 원시 boolean이면 값을 빠뜨렸을 때 false로 바인딩되어
 * "완료 해제"와 구분되지 않는다.
 */
public record ChecklistItemUpdateRequest(
    @NotNull(message = "완료 여부는 필수입니다.") Boolean completed) {
}
