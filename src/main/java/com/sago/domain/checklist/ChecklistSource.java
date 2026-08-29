package com.sago.domain.checklist;

/**
 * 체크리스트 항목의 생성 방식. Gemini 응답 실패 시 STATIC(정적 기본 체크리스트)으로 폴백한다.
 */
public enum ChecklistSource {
    AI,
    STATIC
}
