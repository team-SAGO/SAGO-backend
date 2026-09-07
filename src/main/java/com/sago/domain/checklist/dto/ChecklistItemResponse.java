package com.sago.domain.checklist.dto;

import com.sago.domain.checklist.ChecklistItem;
import com.sago.domain.checklist.ChecklistSource;

import java.time.LocalDateTime;

/**
 * 체크리스트 항목 하나.
 *
 * @param source AI가 생성했는지 정적 기본 목록인지. Gemini 응답이 실패하면 정적 목록으로
 *               폴백하므로, 클라이언트가 필요하면 이를 구분해 안내할 수 있도록 함께 내려준다.
 */
public record ChecklistItemResponse(
    Long checklistItemId,
    String content,
    int orderNo,
    ChecklistSource source,
    boolean completed,
    LocalDateTime completedAt
) {

    public static ChecklistItemResponse from(ChecklistItem item) {
        return new ChecklistItemResponse(
            item.getChecklistItemId(),
            item.getContent(),
            item.getOrderNo(),
            item.getSource(),
            item.isCompleted(),
            item.getCompletedAt()
        );
    }
}
