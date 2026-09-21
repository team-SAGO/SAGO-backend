package com.sago.domain.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 확정 전 경위서 본문 수정.
 *
 * 요약·미확인 항목은 함께 받지 않는다. AI가 본문에서 뽑아낸 값이라, 사용자가 본문만 고쳤을 때
 * 요약을 그대로 두는 편이 사용자가 둘을 각각 맞추는 것보다 낫다고 봤다.
 */
public record ReportUpdateRequest(

    @NotBlank(message = "경위서 본문은 비울 수 없습니다.")
    @Size(max = 20000, message = "경위서 본문은 20000자 이하여야 합니다.")
    String narrative
) {
}
