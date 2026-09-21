package com.sago.domain.report.dto;

import jakarta.validation.constraints.Size;

/**
 * 경위서 작성 요청.
 *
 * 본문을 비워 보내면 AI가 사고 기록·진술·사진 태그를 모아 초안을 만든다. 본문을 담아 보내면 그 내용을
 * 그대로 저장한다 — AI 생성이 실패했을 때 사용자가 직접 쓰는 경로다(기획안 10절 예외처리).
 *
 * @param narrative 직접 작성한 본문. 없으면 AI가 생성한다.
 */
public record ReportWriteRequest(

    @Size(max = 20000, message = "경위서 본문은 20000자 이하여야 합니다.")
    String narrative
) {

    public boolean writtenByUser() {
        return narrative != null && !narrative.isBlank();
    }
}
