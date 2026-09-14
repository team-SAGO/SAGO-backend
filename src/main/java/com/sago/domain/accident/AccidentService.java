package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.accident.dto.AccidentCreation;
import com.sago.domain.accident.dto.AccidentResponse;
import com.sago.domain.user.User;
import com.sago.domain.user.UserNotFoundException;
import com.sago.domain.user.UserRepository;
import com.sago.global.time.ReportedTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Step 2 — 사고 발생 버튼을 눌렀을 때 사고 케이스를 만든다 (FR-02).
 *
 * 여기서 만들어진 accidentId를 이후 체크리스트·진술·사진·경위서가 모두 참조하므로,
 * 사고 대응 플로우의 시작점에 해당한다.
 */
@Service
public class AccidentService {

    /** 진행 중인 사고를 이어서 쓰는 기간. 사고를 만든 시각부터 센다. */
    static final Duration RESUME_WINDOW = Duration.ofHours(1);

    private final AccidentRepository accidentRepository;
    private final UserRepository userRepository;

    public AccidentService(AccidentRepository accidentRepository, UserRepository userRepository) {
        this.accidentRepository = accidentRepository;
        this.userRepository = userRepository;
    }

    /**
     * 사고를 시작한다. 최근에 시작해 아직 진행 중인 사고가 있으면 새로 만들지 않고 그 사고를 돌려준다 (#34).
     *
     * 사고 직후에는 버튼을 두 번 누르거나, 네트워크 재시도가 일어나거나, 앱을 다시 켜고 또 누르기 쉽다.
     * 그때마다 사고가 새로 생기면 체크리스트·진술·사진이 서로 다른 사고에 흩어지고, 사용자가 되돌릴
     * 방법도 없다. 그래서 진행 중인 사고를 이어서 쓰게 한다.
     *
     * <ul>
     *   <li><b>시간 창을 둔다.</b> 무제한으로 이어 쓰면 경위서까지 가지 않고 멈춘 사고에 다음 사고가
     *       붙는다. 중복은 대부분 몇 분 안에 생기고, 한 시간 안에 실제로 두 번째 사고가 날 가능성은 낮다.</li>
     *   <li><b>기준은 발생 시각이 아니라 만든 시각이다.</b> 발생 시각은 사용자가 과거로 입력할 수 있어,
     *       "3시간 전 사고"를 방금 기록하고 다시 누른 경우를 놓치게 된다.</li>
     *   <li><b>이어 쓸 때는 요청 값을 반영하지 않는다.</b> 중복 요청은 대개 같은 값이고, 값을 덮어쓰면
     *       이미 진행한 체크리스트와 사고 정보가 어긋날 수 있다.</li>
     *   <li><b>회원 행을 잠근다.</b> 버튼 두 번은 거의 동시에 도착해 둘 다 "진행 중 사고 없음"을 볼 수 있다.</li>
     * </ul>
     *
     * 사고는 경위서를 확정할 때 {@link Accident#complete()}로 끝난다. 끝난 사고는 이어 쓰지 않는다.
     */
    @Transactional
    public AccidentCreation create(Long userId, AccidentCreateRequest request) {
        // 이어 쓰든 새로 만들든 요청 자체가 올바른지는 먼저 확인한다.
        LocalDateTime occurredAt = ReportedTime.resolve(request.occurredAt(), "사고 발생 시각");

        User user = userRepository.findActiveByIdForUpdate(userId)
            .orElseThrow(() -> new UserNotFoundException("존재하지 않거나 탈퇴한 회원입니다."));

        Optional<Accident> inProgress = accidentRepository
            .findFirstByUser_UserIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                userId, AccidentStatus.IN_PROGRESS, LocalDateTime.now().minus(RESUME_WINDOW));
        if (inProgress.isPresent()) {
            return AccidentCreation.resumed(AccidentResponse.from(inProgress.get()));
        }

        Accident accident = accidentRepository.save(Accident.builder()
            .user(user)
            .accidentType(request.accidentType())
            .injurySelf(request.injurySelf())
            .injuryOther(request.injuryOther())
            .occurredAt(occurredAt)
            .latitude(request.latitude())
            .longitude(request.longitude())
            .direction(request.direction())
            .roadCondition(request.roadCondition())
            .memo(request.memo())
            .build());

        return AccidentCreation.created(AccidentResponse.from(accident));
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
