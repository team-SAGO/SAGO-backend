package com.sago.domain.accident;

import com.sago.domain.accident.dto.AccidentCreateRequest;
import com.sago.domain.accident.dto.AccidentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccidentResponse create(@AuthenticationPrincipal Long userId,
                                    @Valid @RequestBody AccidentCreateRequest request) {
        return accidentService.create(userId, request);
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
