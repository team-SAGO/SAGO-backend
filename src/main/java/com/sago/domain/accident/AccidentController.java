package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.accident.dto.AccidentCreation;
import com.sago.domain.accident.dto.AccidentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사고 시작 (FR-02).
 *
 * 사고 주인은 요청 본문이 아니라 토큰의 principal로 결정한다 — 클라이언트가 userId를 보내게 하면
 * 남의 이름으로 사고를 만들 수 있다.
 */
@RestController
@RequestMapping("/api/accidents")
public class AccidentController {

    private final AccidentService accidentService;

    public AccidentController(AccidentService accidentService) {
        this.accidentService = accidentService;
    }

    /**
     * 사고 시작.
     *
     * 새 사고를 만들면 201, 설정한 기간 안에 시작한 진행 중 사고가 있어 그것을 돌려주면 200이다.
     * 200이면 요청 본문은 반영되지 않으므로, 클라이언트는 "진행 중인 사고를 이어서 진행합니다"처럼 안내한다.
     * 다른 사고라면 사용자가 이전 사고를 종료(POST /{accidentId}/complete)한 뒤 다시 시작하면 된다.
     */
    @PostMapping
    public ResponseEntity<AccidentResponse> create(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody AccidentCreateRequest request) {
        AccidentCreation result = accidentService.create(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(result.accident());
    }

    /**
     * 사고 처리 종료.
     *
     * 끝낸 사고는 이어 쓰기 대상에서 빠지므로, 다음에 사고 발생 버튼을 누르면 새 사고가 만들어진다.
     * 이미 끝난 사고에 다시 불러도 성공한다.
     */
    @PostMapping("/{accidentId}/complete")
    public AccidentResponse complete(@AuthenticationPrincipal Long userId,
                                     @PathVariable Long accidentId) {
        return accidentService.complete(userId, accidentId);
    }

    /**
     * 내 사고 이력. 최근 사고가 먼저 온다.
     * 홈 화면의 "최근 사고 이력"도 이 API를 쓴다 — 같은 목록이라 따로 두지 않았다.
     */
    @GetMapping
    public List<AccidentResponse> getMyAccidents(@AuthenticationPrincipal Long userId) {
        return accidentService.getMyAccidents(userId);
    }

    /** 사고 상세. 남의 사고는 404다. */
    @GetMapping("/{accidentId}")
    public AccidentResponse getAccident(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long accidentId) {
        return accidentService.getAccident(userId, accidentId);
    }
}
