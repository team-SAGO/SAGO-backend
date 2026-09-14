package com.sago.domain.checklist;

import com.sago.domain.accident.AccidentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 체크리스트의 DB 작업만 담당한다.
 *
 * {@link ChecklistService}에서 분리한 이유는 Gemini 호출을 트랜잭션 밖에 두기 위해서다.
 * 같은 빈 안에서 메서드만 나누면 프록시가 적용되지 않아 별도 빈으로 뺐다
 * (#27의 SocialAccountRegistrar, #38의 ProfileImageStore와 같은 구조).
 */
@Component
public class ChecklistStore {

    private final ChecklistItemRepository checklistItemRepository;
    private final AccidentRepository accidentRepository;

    public ChecklistStore(ChecklistItemRepository checklistItemRepository,
                          AccidentRepository accidentRepository) {
        this.checklistItemRepository = checklistItemRepository;
        this.accidentRepository = accidentRepository;
    }

    @Transactional(readOnly = true)
    public List<ChecklistItem> findItems(Long accidentId) {
        return checklistItemRepository.findByAccident_AccidentIdOrderByOrderNoAsc(accidentId);
    }

    /**
     * 아직 체크리스트가 없을 때만 저장하고, 이미 있으면 기존 것을 그대로 돌려준다.
     *
     * 사고 행에 쓰기 락을 걸어 확인과 저장 사이에 다른 요청이 끼어들지 못하게 한다.
     * 락이 없으면 사고 직후 화면 진입 요청이 겹치거나 사용자가 새로고침을 연타할 때 양쪽 다
     * "비어 있다"고 보고 각각 저장해, 항목이 두 벌 남고 화면에 중복으로 보인다.
     *
     * 락은 이 DB 작업 동안만 잡는다. Gemini 호출은 이 메서드에 들어오기 전에 이미 끝나 있다.
     * 경합에서 진 쪽은 방금 생성한 항목을 버리게 되는데, Gemini 호출 한 번이 낭비되는 대신
     * 중복 데이터가 남지 않는다. 중복은 사용자가 스스로 정리할 방법이 없어 더 비싸다.
     */
    @Transactional
    public List<ChecklistItem> saveIfAbsent(Long accidentId, List<ChecklistItem> generated) {
        // 반환값을 쓰지 않는 호출이다. 사고 행에 쓰기 락을 걸어 아래 확인·저장 구간을
        // 한 번에 하나만 지나가게 하는 것이 목적이라, 조회 결과 자체는 필요 없다.
        accidentRepository.findByIdForUpdate(accidentId);

        List<ChecklistItem> existing =
            checklistItemRepository.findByAccident_AccidentIdOrderByOrderNoAsc(accidentId);
        if (!existing.isEmpty()) {
            return existing;
        }

        return checklistItemRepository.saveAll(generated);
    }
}
