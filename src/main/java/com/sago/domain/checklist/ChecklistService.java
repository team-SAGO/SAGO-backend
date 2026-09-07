package com.sago.domain.checklist;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentService;
import com.sago.domain.checklist.dto.ChecklistItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Step 3 — 사고 대응 체크리스트 조회와 완료 저장 (FR-03).
 *
 * 체크리스트 생성 자체는 {@link ChecklistGenerationService}가 담당하고, 여기서는
 * 언제 생성할지와 누가 접근할 수 있는지를 정한다.
 */
@Service
public class ChecklistService {

    private final AccidentService accidentService;
    private final ChecklistGenerationService checklistGenerationService;
    private final ChecklistItemRepository checklistItemRepository;

    public ChecklistService(AccidentService accidentService,
                            ChecklistGenerationService checklistGenerationService,
                            ChecklistItemRepository checklistItemRepository) {
        this.accidentService = accidentService;
        this.checklistGenerationService = checklistGenerationService;
        this.checklistItemRepository = checklistItemRepository;
    }

    /**
     * 체크리스트를 돌려준다. 아직 없으면 이때 생성한다.
     *
     * 사고 생성 시점이 아니라 첫 조회 때 만드는 이유는 두 가지다.
     * 1. Gemini 호출은 읽기 타임아웃이 30초다. 사고 발생 버튼은 사고 직후에 눌리는 것이라
     *    그 시간을 기다리게 할 수 없다.
     * 2. 사고 생성이 나중에 "진행 중 사고가 있으면 그걸 반환"으로 바뀌어도(#34), 이미 항목이
     *    있으면 그대로 돌려주므로 중복 생성되지 않는다.
     */
    @Transactional
    public List<ChecklistItemResponse> getOrGenerate(Long userId, Long accidentId) {
        Accident accident = accidentService.getOwnedAccident(userId, accidentId);

        List<ChecklistItem> items =
            checklistItemRepository.findByAccident_AccidentIdOrderByOrderNoAsc(accidentId);
        if (items.isEmpty()) {
            items = checklistGenerationService.generateChecklist(accident);
        }

        return items.stream().map(ChecklistItemResponse::from).toList();
    }

    /**
     * 항목의 완료 여부를 바꾼다.
     *
     * 완료뿐 아니라 해제도 받는다. 사고 직후 급한 상황에서 누르는 화면이라 잘못 체크하는 일이
     * 생기는데, 되돌릴 수 없으면 하지 않은 일을 완료로 남긴 채 다음 단계로 넘어가게 된다.
     */
    @Transactional
    public ChecklistItemResponse updateCompletion(Long userId, Long accidentId,
                                                   Long checklistItemId, boolean completed) {
        // 사고 소유권부터 확인한다. 항목만 보고 판단하면 남의 사고 항목을 건드릴 수 있다.
        accidentService.getOwnedAccident(userId, accidentId);

        ChecklistItem item = checklistItemRepository.findById(checklistItemId)
            .orElseThrow(() -> new ChecklistItemNotFoundException("체크리스트 항목을 찾을 수 없습니다."));

        // 다른 사고의 항목 번호를 끼워 넣는 경우를 막는다.
        if (!item.belongsTo(accidentId)) {
            throw new ChecklistItemNotFoundException("체크리스트 항목을 찾을 수 없습니다.");
        }

        if (completed) {
            item.complete();
        } else {
            item.uncomplete();
        }

        return ChecklistItemResponse.from(item);
    }
}
