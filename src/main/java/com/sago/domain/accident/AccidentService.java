package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.accident.dto.AccidentResponse;
import com.sago.domain.user.User;
import com.sago.domain.user.UserNotFoundException;
import com.sago.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Step 2 — 사고 발생 버튼을 눌렀을 때 사고 케이스를 만든다 (FR-02).
 *
 * 여기서 만들어진 accidentId를 이후 체크리스트·진술·사진·경위서가 모두 참조하므로,
 * 사고 대응 플로우의 시작점에 해당한다.
 */
@Service
public class AccidentService {

    private final AccidentRepository accidentRepository;
    private final UserRepository userRepository;

    public AccidentService(AccidentRepository accidentRepository, UserRepository userRepository) {
        this.accidentRepository = accidentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccidentResponse create(Long userId, AccidentCreateRequest request) {
        User user = userRepository.findByUserIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다."));

        Accident accident = accidentRepository.save(Accident.builder()
            .user(user)
            .accidentType(request.accidentType())
            .injurySelf(request.injurySelf())
            .injuryOther(request.injuryOther())
            // 발생 시각을 보내지 않았다면 버튼을 누른 지금을 사고 시각으로 본다.
            .occurredAt(request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now())
            .latitude(request.latitude())
            .longitude(request.longitude())
            .direction(request.direction())
            .roadCondition(request.roadCondition())
            .memo(request.memo())
            .build());

        return AccidentResponse.from(accident);
    }

    /**
     * 본인 사고인지 확인하고 돌려준다.
     *
     * 남의 사고를 조회했을 때 403이 아니라 404를 내려준다 — 403은 "그 번호의 사고가 존재한다"는
     * 사실을 알려주는 셈이라, 번호를 바꿔가며 사고 존재 여부를 알아낼 수 있기 때문이다.
     */
    @Transactional(readOnly = true)
    public Accident getOwnedAccident(Long userId, Long accidentId) {
        Accident accident = accidentRepository.findById(accidentId)
            .orElseThrow(() -> new AccidentNotFoundException("사고 기록을 찾을 수 없습니다."));

        if (!accident.isOwnedBy(userId)) {
            throw new AccidentNotFoundException("사고 기록을 찾을 수 없습니다.");
        }
        return accident;
    }
}
