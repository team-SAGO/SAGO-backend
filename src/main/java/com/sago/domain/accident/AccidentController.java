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
     * 새 사고를 만들면 201, 한 시간 안에 시작한 진행 중 사고가 있어 그것을 돌려주면 200이다.
     * 200이면 요청 본문은 반영되지 않으므로, 클라이언트는 "진행 중인 사고를 이어서 진행합니다"처럼 안내한다.
     */
    @PostMapping
    public ResponseEntity<AccidentResponse> create(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody AccidentCreateRequest request) {
        AccidentCreation result = accidentService.create(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(result.accident());
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
