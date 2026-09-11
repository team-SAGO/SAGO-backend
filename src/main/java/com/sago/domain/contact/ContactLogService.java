package com.sago.domain.contact;

import com.sago.domain.accident.Accident;
import com.sago.domain.accident.AccidentService;
import com.sago.domain.contact.dto.ContactLogCreateRequest;
import com.sago.domain.contact.dto.ContactLogResponse;
import com.sago.global.time.ReportedTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 신고·보험사 연결 기록 (FR-07).
 *
 * 외부 호출이 없는 단순 저장·조회라 트랜잭션으로 감싼다.
 */
@Service
public class ContactLogService {

    private final AccidentService accidentService;
    private final ContactLogRepository contactLogRepository;

    public ContactLogService(AccidentService accidentService,
                             ContactLogRepository contactLogRepository) {
        this.accidentService = accidentService;
        this.contactLogRepository = contactLogRepository;
    }

    /**
     * 연결 시도를 기록한다.
     *
     * 사고 발생 시각보다 앞선 시각이어도 거부하지 않는다. 발생 시각도 사용자가 보고한 값이라
     * 대략적일 수 있어서, 그 기준으로 막으면 실제로 일어난 신고 기록이 남지 않을 수 있다.
     *
     * 구체적으로 두 경우가 막힌다.
     * <ul>
     *   <li>가장 흔한 흐름 — 사고 버튼을 누르고 바로 112에 거는 경우. 발생 시각은 기기 시계로,
     *       연결 시각은 생략해 서버 시계로 정해지는데, 기기 시계가 몇 분 빠르면(허용 범위 안)
     *       연결이 발생보다 앞선 것으로 보인다.</li>
     *   <li>발생 시각을 잘못 입력한 경우. 사고 기록을 수정하는 API가 없어, 연결 기록을 막으면
     *       사용자는 실제 연결 시각을 남길 방법이 없고 틀린 시각을 넣도록 떠밀린다.</li>
     * </ul>
     * 두 시각의 앞뒤가 어긋나는 건 저장이 아니라 경위서를 만들 때 드러내는 편이 맞다.
     */
    @Transactional
    public ContactLogResponse record(Long userId, Long accidentId, ContactLogCreateRequest request) {
        Accident accident = accidentService.getOwnedAccident(userId, accidentId);

        ContactLog saved = contactLogRepository.save(ContactLog.builder()
            .accident(accident)
            .contactType(request.contactType())
            .contactedAt(ReportedTime.resolve(request.contactedAt(), "연결 시각"))
            .build());

        return ContactLogResponse.from(saved);
    }

    /** 사고의 연결 기록. 시도한 순서대로 나온다. */
    @Transactional(readOnly = true)
    public List<ContactLogResponse> getContactLogs(Long userId, Long accidentId) {
        accidentService.getOwnedAccident(userId, accidentId);

        return contactLogRepository.findByAccident_AccidentIdOrderByContactedAtAsc(accidentId).stream()
            .map(ContactLogResponse::from)
            .toList();
    }
}
