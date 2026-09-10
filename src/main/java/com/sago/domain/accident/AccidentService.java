package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.accident.dto.AccidentResponse;
import com.sago.domain.user.User;
import com.sago.domain.user.UserNotFoundException;
import com.sago.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Step 2 — 사고 발생 버튼을 눌렀을 때 사고 케이스를 만든다 (FR-02).
 *
 * 여기서 만들어진 accidentId를 이후 체크리스트·진술·사진·경위서가 모두 참조하므로,
 * 사고 대응 플로우의 시작점에 해당한다.
 */
@Service
public class AccidentService {

    /** 클라이언트와 서버의 시계 차이를 감안해 미래로 허용하는 범위. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

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
            .occurredAt(resolveOccurredAt(request.occurredAt()))
            .latitude(request.latitude())
            .longitude(request.longitude())
            .direction(request.direction())
            .roadCondition(request.roadCondition())
            .memo(request.memo())
            .build());

        return AccidentResponse.from(accident);
    }

    /**
     * 내 사고 목록. 최근에 일어난 사고가 먼저 온다.
     *
     * 토큰의 userId로만 조회하므로 남의 사고가 섞일 여지가 없다 —
     * 상세 조회와 달리 소유권을 따로 확인할 필요가 없는 이유다.
     *
     * 페이지네이션은 두지 않았다. 개인의 사고 이력은 많아야 수십 건이라 페이지를 나누면
     * 클라이언트만 복잡해진다. 이력이 많은 사용자가 생기면 그때 추가한다.
     */
    @Transactional(readOnly = true)
    public List<AccidentResponse> getMyAccidents(Long userId) {
        return accidentRepository.findByUser_UserIdOrderByOccurredAtDesc(userId).stream()
            .map(AccidentResponse::from)
            .toList();
    }

    /** 사고 상세. 본인 사고가 아니면 404다. */
    @Transactional(readOnly = true)
    public AccidentResponse getAccident(Long userId, Long accidentId) {
        return AccidentResponse.from(getOwnedAccident(userId, accidentId));
    }

    /**
     * 발생 시각을 정한다.
     *
     * 보내지 않았다면 버튼을 누른 지금을 사고 시각으로 본다.
     *
     * 미래 시각은 거부한다. 이 값은 경위서와 보험 서류에 그대로 실리므로, 기기 시간이 틀어져
     * 들어온 값을 그대로 저장하면 사후에 바로잡기 어렵다. 다만 클라이언트와 서버의 시계가
     * 몇 초 어긋나는 건 흔한 일이라, 그 정도까지 반려하면 정상 신고가 막힌다 —
     * 그래서 @PastOrPresent로 딱 잘라 검증하지 않고 여유를 둔다.
     */
    private LocalDateTime resolveOccurredAt(LocalDateTime occurredAt) {
        if (occurredAt == null) {
            return LocalDateTime.now();
        }
        if (occurredAt.isAfter(LocalDateTime.now().plus(FUTURE_TOLERANCE))) {
            throw new IllegalArgumentException("사고 발생 시각은 미래일 수 없습니다.");
        }
        return occurredAt;
    }

    /**
     * 본인 사고인지 확인하고 돌려준다.
     *
     * 남의 사고를 조회했을 때 403이 아니라 404를 내려준다 — 403은 "그 번호의 사고가 존재한다"는
     * 사실을 알려주는 셈이라, 번호를 바꿔가며 사고 존재 여부를 알아낼 수 있기 때문이다.
     *
     * <p><b>호출 규약:</b> 돌려주는 엔티티는 <b>호출자의 트랜잭션 안에서 쓰는 것을 전제</b>로 한다.
     * {@code Accident.user}가 지연 로딩이고 {@code open-in-view: false}라, 트랜잭션 밖에서
     * {@code getUser()}의 필드를 건드리면 {@code LazyInitializationException}이 난다.
     * ({@link Accident#isOwnedBy}는 식별자만 보므로 밖에서도 안전하다.)
     * 사고 소유권을 확인하는 다른 도메인 기능은 자신의 {@code @Transactional} 안에서 호출할 것.
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
