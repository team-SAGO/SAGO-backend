package com.sago.domain.checklist;

import com.sago.domain.checklist.dto.ChecklistItemResponse;
import com.sago.domain.checklist.dto.ChecklistItemUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사고 대응 체크리스트 (FR-03).
 *
 * 사고 하위 리소스이므로 경로를 사고 아래에 둔다. 항목 번호만으로 접근하게 하면 어느 사고의
 * 항목인지 서버가 확인할 근거가 요청에 없어, 남의 사고 항목을 지정해 수정할 여지가 생긴다.
 */
@RestController
@RequestMapping("/api/accidents/{accidentId}/checklist")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    /** 체크리스트 조회. 아직 만들어지지 않았다면 이 호출에서 생성된다. */
    @GetMapping
    public List<ChecklistItemResponse> getChecklist(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long accidentId) {
        return checklistService.getOrGenerate(userId, accidentId);
    }

    /** 항목 완료/해제. */
    @PatchMapping("/{checklistItemId}")
    public ChecklistItemResponse updateItem(@AuthenticationPrincipal Long userId,
                                             @PathVariable Long accidentId,
                                             @PathVariable Long checklistItemId,
                                             @Valid @RequestBody ChecklistItemUpdateRequest request) {
        return checklistService.updateCompletion(
            userId, accidentId, checklistItemId, request.completed());
    }
}
